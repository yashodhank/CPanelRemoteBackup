/*  Copyright (C) 2012 Carsten Lergenmüller

    Permission is hereby granted, free of charge, to any person obtaining a copy of this
    software and associated documentation files (the "Software"), to deal in the Software
    without restriction, including without limitation the rights to use, copy, modify,
    merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
    permit persons to whom the Software is furnished to do so, subject to the following
    conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
        INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
        PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
        HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
        OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
        SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package com.unispezi.cpanelremotebackup.ftp;

import com.unispezi.cpanelremotebackup.tools.Log;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * This class is an FTP client. It currently just hides an Apache HTTPComponents
 * FTP client, but since all FTP client logic is hidden behind this
 * class, this could later be changed.
 */
public class FTPClient {

    private org.apache.commons.net.ftp.FTPClient ftp;
    private String host;
    private String password;
    private String user;
    private boolean isLoggedIn;

    public FTPClient(String host, String user, String password) {
        this.host = host;
        this.password = password;
        this.user = user;

        // Bring up FTP client and make sure it uses passive mode
        ftp = new org.apache.commons.net.ftp.FTPClient(){
            @Override
            protected void _connectAction_() throws IOException {
                super._connectAction_();
                enterLocalPassiveMode();
            }
        };
    }

    public void connect() throws FTPException {
        if (!ftp.isConnected()) {
            String logString = "Connecting to  " + getHostLogString();

            try {

                //Connect
                Log.debug(logString + "...");
                ftp.connect(host);
                Log.debug("Reply string was:" + ftp.getReplyString());


                // Check for errors
                try{
                    checkResult(ftp.getReplyCode(), logString);
                } catch (FTPException e){
                    ftp.disconnect();
                    throw e;
                }
            } catch (IOException e) {
                throw handleException(e, logString);
            }

        } else {
            Log.debug("Ignoring connect(), already connected to " + getHostLogString() + "");
        }
    }

    public void login() throws FTPException {
        if (! isLoggedIn){
            if (!ftp.isConnected()) {
                connect();
            }

            String logString = "Logging in to  " + getHostLogString();
            try {
                //Log in
                Log.debug(logString + "," + " user=\"" + user + "\", password starts with \"" + password.substring(0, 1) + "\"");
                boolean successful = ftp.login(user, password);
                Log.debug("Reply string was:" + ftp.getReplyString());

                checkResult(ftp.getReplyCode(), logString, successful);
                isLoggedIn = true;

                // Check for errors
                // reply code check only necessary after connect
            } catch (IOException e) {
                throw handleException(e, logString);
            }
        } else {
            Log.info("Ignoring logon call, already logged on to " + getHostLogString());
        }
    }
    
    private String getHostLogString(){
        return "\"" + host + "\"";
    }


    public void logout() {
        if (isLoggedIn) {
            if (ftp.isConnected()) {
                String logString = "Logout from  " + getHostLogString();
                try {

                    //Logout
                    Log.debug(logString + "...");
                    ftp.logout();
                    Log.debug("Reply string was:" + ftp.getReplyString());

                    // Check for errors
                    checkResult(ftp.getReplyCode(), logString);
                } catch (IOException e) {
                    throw handleException(e, logString);
                }
            } else {
                Log.debug("Ignoring logout(), not connected to " + getHostLogString() + "");
            }
        } else {
            Log.debug("Ignoring logout(), not logged in to " + getHostLogString() + "");
        }
    }

    public List<FTPFile> listFilesInDirectory(String directory) {

        String logString = "Listing directory \"" + directory + "\"";

        assertLoggedIn(logString);
        try {
            //Logout
            Log.debug(logString);

            FTPFile[] files = ftp.listFiles(directory);
            Log.debug("Reply string was:" + ftp.getReplyString());

            // Check for errors
            checkResult(ftp.getReplyCode(), logString);

            return Arrays.asList(files);
        } catch (IOException e) {
            throw handleException(e, logString);
        }
    }

    public FTPFile getFileDetails(String filePath) {

        String logString = "Mlisting file \"" + filePath + "\"";

        assertLoggedIn(logString);
        try {
            //Logout
            Log.debug(logString);

            FTPFile file = ftp.mlistFile(filePath);
            Log.debug("Reply string was:" + ftp.getReplyString());

            // Check for errors
            checkResult(ftp.getReplyCode(), logString);

            return file;
        } catch (IOException e) {
            throw handleException(e, logString);
        }
    }

    public void downloadFile(String filePath, OutputStream stream) {

        String logString = "Downloading \"" + filePath+ "\"";

        assertLoggedIn(logString);
        try {

            String origLogString = logString;
            logString = "Setting file type to binary"; //Patch over for handleException below
            Log.debug(logString);

            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            Log.debug("Reply string was:" + ftp.getReplyString());

            checkResult(ftp.getReplyCode(), logString);

            logString = origLogString;

            //Logout
            Log.debug(logString);

            ftp.retrieveFile(filePath, stream);
            Log.debug("Reply string was:" + ftp.getReplyString());

            stream.close();

            // Check for errors
            checkResult(ftp.getReplyCode(), logString);

        } catch (IOException e) {
            throw handleException(e, logString);
        }
    }

    /**
     * Deletes file from server, will relogin if connection was lost.
     *
     * @param filePath file to delete
     */
    public void deleteFile(String filePath) {
        deleteFile(filePath, false);
    }

    /**
     * Deletes file from server, will relogin if connection was lost.
     *
     * @param filePath file to delete
     * @param isRetry true if this is a retry
     */
    private void deleteFile(String filePath, boolean isRetry) {

        String logString = "Deleting \"" + filePath+ "\"";

        assertLoggedIn(logString);
        try {

            //Delete
            Log.debug(logString);

            boolean deleted = ftp.deleteFile(filePath);
            Log.debug("Reply string was:" + ftp.getReplyString());

            //Retry if connection was closed
            if (!isRetry && (ftp.getReplyCode() == 421)) { //"Connection already closed"
                deleteFile(filePath, true);
            } else {
                // Check for errors
                checkResult(ftp.getReplyCode(), logString, deleted);
            }

        } catch (IOException e) {
            throw handleException(e, logString);
        }
    }




    private void assertLoggedIn(String logString) {
        if (!isLoggedIn || !ftp.isConnected()) {
            Log.error(logString + " failed because we are not logged in.");
            throw new FTPException("You are not logged in to " + getHostLogString());
        }
    }

    private RuntimeException handleException(IOException e, String logString) {
        Log.error(logString + " failed because of " + e);
        throw new FTPException(logString + " failed", e);
    }

    private void checkResult(int replyCode, String logString) {
        checkResult(replyCode, logString, true);
    }

    private void checkResult(int replyCode, String logString, boolean customSuccessful) {
        if (FTPReply.isPositiveCompletion(replyCode)) {
            if (customSuccessful) {
                Log.info(logString + " successful");
            } else {
                Log.error(logString + " failed");
                throw new FTPException(logString + " failed");
            }
        } else {
            Log.error(logString + " failed with reply code " + replyCode);
            if (ftp.getReplyCode() == 421) { //"Connection already closed"
                isLoggedIn = false;
            }
            throw new FTPException(logString + " failed with reply code " + replyCode);
        }
    }
}
