/*
 Ext2Attr is a tiny frontend for command line 'library' e2imm
 'Immutable/Append' and changing 'Immutable' attribute

 Copyright (C) 2018 duangsuse

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.duangsuse.attrtools;

import android.app.AlertDialog;
import android.app.AppGlobals;
import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.widget.Toast;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Ext2 like filesystem file attribute manipulation library
 *
 * @author duangsuse
 * @author trumeet
 * @version 1.3
 * @see "https://github.com/duangsuse/attrtools"
 * @see "e2immutable.c"
 * @since 1.0
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Ext2Attr implements Closeable {
    private static final String TAG = "Ext2Attr";
    private static final String DEFAULT_LIB_PATH = "e2im";
    private static final String DEFAULT_SU_PATH = "su";

    public static final int RESULT_CHANGED = 0;
    public static final int RESULT_UNCHANGED = 1;

    public static final int ERR_NO_SUCH_FILE = -1;
    public static final int ERR_UNKNOWN = -2;
    public static final int ERR_MESSAGE = -3;

    /**
     * Native result "i" only
     */
    public static final int ATTRIBUTE_I = 1;
    /**
     * Native result "a" only
     */
    public static final int ATTRIBUTE_A = 2;
    /**
     * Native result "i" and "a"
     */
    public static final int ATTRIBUTE_I_A = 3;
    public static final String LIBE2IM_NAME = "/libe2im.so";

    /**
     * Status change state
     */
    @IntDef(value = {
            RESULT_CHANGED,
            RESULT_UNCHANGED
    })
    @Retention(RetentionPolicy.CLASS)
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    private @interface ChangeStatus {
    }

    /**
     * Error codes
     */
    @IntDef(value = {
            ERR_NO_SUCH_FILE,
            ERR_MESSAGE,
            ERR_UNKNOWN
    })
    @Retention(RetentionPolicy.CLASS)
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    private @interface ErrorCode {
    }

    /**
     * File attributes
     */
    @IntDef(value = {
            ATTRIBUTE_I,
            ATTRIBUTE_A,
            ATTRIBUTE_I_A
    })
    @Retention(RetentionPolicy.CLASS)
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    private @interface Attribute {
    }

    /**
     * Command to execute format
     */
    private static final String COMMAND_FMT = "%1$s %2$s %3$s;printf $?_";

    /**
     * E2IM executable path
     */
    private final String lib_path;

    /**
     * Shell executable path
     */
    private final String su_path;

    /**
     * Shell process instance
     */
    public Process shell = null;

    /**
     * Shell stdin
     */
    public PrintStream stdin = null;

    /**
     * Shell stdout
     */
    public Scanner stdout = null;

    /**
     * Shell stderr
     */
    public Scanner stderr = null;

    /**
     * Default constructor
     *
     * @param su_path  superuser binary path
     * @param lib_path e2im program path
     */
    public Ext2Attr(String su_path, String lib_path) {
        this.su_path = su_path;
        this.lib_path = lib_path;
    }

    /**
     * Construct a new instance using custom executable
     *
     * @param lib_path e2im executable library path
     */
    public Ext2Attr(String lib_path) {
        this(DEFAULT_SU_PATH, lib_path);
    }

    /**
     * Create instance for Android with default config
     *
     * @param context application context
     */
    public Ext2Attr(@NonNull Context context) {
        this(DEFAULT_SU_PATH, getExecPath(context));
    }

    /**
     * Get Ext2Attr initialized using default Android context
     *
     * @deprecated Use {@link #Ext2Attr(Context)} instead
     */
    @Deprecated
    @MainThread
    public Ext2Attr() {
        this(AppGlobals.getInitialApplication());
    }

    /**
     * Gets android app libe2im native library path
     *
     * @param ctx app context
     * @return full path of executable
     */
    @NonNull
    public static String getExecPath(Context ctx) {
        return ctx.getApplicationInfo().nativeLibraryDir + LIBE2IM_NAME;
    }

    /**
     * Map native error message to android translated text
     *
     * @param ctx     app context
     * @param message text to translate
     * @return translated text
     */
    @NonNull
    public static String mapMessage(Context ctx, String message) {
        if (message == null)
            return ctx.getString(R.string.no_perm);

        switch (message) {
            case "Function not implemented":
                return ctx.getString(R.string.err_func_no_imp);
            case "Not a typewriter":
                return ctx.getString(R.string.err_not_typewriter);
            case "Operation not supported":
                return ctx.getString(R.string.err_not_supp);
            case "Operation not supported on transport endpoint":
                return ctx.getString(R.string.err_not_supported);
            case "Inappropriate ioctl for device":
                return ctx.getString(R.string.err_bad_ioctl);
            case "Operation not permitted":
                return ctx.getString(R.string.err_no_perm);
        }
        return message;
    }

    /**
     * Show 'acquire root' dialog<p>
     * <strong>Warning: you must reset default exception handler after calling this method</strong><p>
     * This dialog won't be closed until root shell is created
     *
     * @param ctx app context
     * @param fn  function to execute after shell created
     */
    public static void acquireRoot(Context ctx, Runnable fn) {
        Ext2Attr instance = new Ext2Attr(getExecPath(ctx));
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        });

        Timer timer = new Timer();

        AlertDialog a = new AlertDialog.Builder(ctx)
                .setTitle(R.string.acquire_root)
                .setIcon(R.drawable.icon)
                .setMessage(R.string.wait_for_perm)
                .setCancelable(false)
                .setOnCancelListener((v) -> fn.run()).show();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // noinspection StatementWithEmptyBody
                while (instance.isNotConnected());
                a.cancel();
            }
        }, 100);
    }

    /**
     * Disconnect form shell
     */
    @Override
    public void close() {
        executeCommand("exit");
        try {
            shell.waitFor();
        } catch (InterruptedException e) {
            Log.e(TAG, "close", e);
        }
    }

    /**
     * Connect with shell, running on the main thread is strongly not recommend.
     *
     * @return Is connected successfully
     */
    @WorkerThread
    public boolean connect() {
        try {
            shell = Runtime.getRuntime().exec(this.su_path);
        } catch (IOException e) {
            Log.e(TAG, "connect", e);
            return false;
        }

        if (shell == null)
            return false;

        stdin = new PrintStream(shell.getOutputStream());
        stdout = new Scanner(new DataInputStream(shell.getInputStream()));
        stderr = new Scanner(new DataInputStream(shell.getErrorStream()));
        stdout.useDelimiter("_");
        return true;
    }

    /**
     * Not connected with shell?
     *
     * @return is not connected with root shell
     * @deprecated use {@link #isNotConnected()} instead.
     */
    @SuppressWarnings("WeakerAccess")
    @Deprecated
    public boolean not_connected() {
        return isNotConnected();
    }

    public boolean isNotConnected() {
        return shell == null || stdin == null || !isAlive(shell);
    }

    /**
     * Is a process alive?
     *
     * @param p target process object
     * @return true if alive
     */
    private static boolean isAlive(Process p) {
        boolean ret = false;
        try {
            p.exitValue();
        } catch (IllegalThreadStateException ignored) {
            ret = true;
        }
        return ret;
    }

    /**
     * Query file attribute
     *
     * @param path path to query, must be standard file
     * @return <strong>0</strong> for no attribute<p>
     * <strong>1</strong> for +i<p>
     * <strong>2</strong> for +a<p>
     * <strong>3</strong> for +i+a<p>
     * <strong>-1</strong> for no file
     * @throws ShellException reading attr fails
     */
    @Attribute
    public int query(String path) throws ShellException {
        String command = String.format(COMMAND_FMT, lib_path, '@', path);
        executeCommand(command);

        int result = stdout.nextInt();
        switch (result) {
            case ATTRIBUTE_I:
                return ATTRIBUTE_I;
            case ATTRIBUTE_A:
                return ATTRIBUTE_A;
            case ATTRIBUTE_I_A:
                return ATTRIBUTE_I_A;
            default:
                // Parse other result codes
                readException(result);
                return ATTRIBUTE_I; // Never reached
        }
    }

    /**
     * File attribute +i (make immutable)
     *
     * @param path path to add immutable, must be standard file
     * @return <strong>0</strong> for changed<p>
     * <strong>1</strong> for unchanged<p>
     * <stong>-1</stong> for no file
     * @throws ShellException reading or changing attr fails
     */
    @ChangeStatus
    public int addi(String path) throws ShellException {
        String command = String.format(COMMAND_FMT, lib_path, '+', path);
        executeCommand(command);

        int result = stdout.nextInt();
        switch (result) {
            case RESULT_CHANGED:
                return RESULT_CHANGED;
            case RESULT_UNCHANGED:
                return RESULT_UNCHANGED;
            default:
                // Parse other result codes
                readException(result);
                return RESULT_UNCHANGED; // Never reached
        }
    }

    /**
     * File attribute -i (make mutable)
     *
     * @param path path to query, must be standard file
     * @return <strong>0</strong> for changed<p>
     * <strong>1</strong> for unchanged<p>
     * <strong>-1</strong> for no file
     * @throws ShellException reading or changing attr fails
     */
    @ChangeStatus
    public int subi(String path) throws ShellException {
        String command = String.format(COMMAND_FMT, lib_path, '-', path);
        executeCommand(command);

        int result = stdout.nextInt();
        switch (result) {
            case RESULT_CHANGED:
                return RESULT_CHANGED;
            case RESULT_UNCHANGED:
                return RESULT_UNCHANGED;
            default:
                // Parse other result codes
                readException(result);
                return RESULT_UNCHANGED; // Never reached
        }
    }

    /**
     * Read e2im util errno to exception
     *
     * @param value exit value
     * @throws ShellException always throws
     */
    private void readException(int value) throws ShellException {
        Log.e(TAG, "Handling error code from native: " + value);
        switch (value) {
            case 255:
                throw new ShellException(ERR_NO_SUCH_FILE);
            case 254:
                throw new ShellException(stderr.nextLine());
            default:
                throw new ShellException(ERR_UNKNOWN);
        }
    }

    public void executeCommand (String command) {
        stdin.println(command);
        stdin.flush();
    }

    /**
     * Exception explaining the e2im binary is returning an error instead of result
     *
     * @author trumeet
     * @since 1.3
     */
    public static class ShellException extends Exception {
        @ErrorCode
        private int code;
        private String message;

        /**
         * Construct a new instance using error message
         *
         * @param message error message
         */
        public ShellException(String message) {
            this(ERR_MESSAGE, message);
        }

        /**
         * Construct a new instance using error code and message
         *
         * @param code    error code
         * @param message error message
         */
        public ShellException(@ErrorCode int code, @Nullable String message) {
            super(message);
            this.code = code;
            this.message = message;
        }

        /**
         * Construct a new instance using error code
         *
         * @param code exit code
         */
        public ShellException(@ErrorCode int code) {
            this(code, null);
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        @Override
        public String getMessage() {
            return message;
        }

        /**
         * @deprecated It's bad to use default Android context, use {@link #getTranslatedMessage(Context)}
         */
        @Override
        @MainThread
        @Deprecated
        public String getLocalizedMessage() {
            return mapMessage(AppGlobals.getInitialApplication(), message);
        }

        /**
         * Gets translated native error message
         *
         * @param ctx Android context
         * @return translated error message
         */
        public String getTranslatedMessage(Context ctx) {
            return mapMessage(ctx, message);
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("(%1$s)%2$s", getCode(), getMessage());
        }
    }
}
