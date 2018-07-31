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
import android.content.Context;
import android.widget.Toast;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Ext2 like filesystem file attribute manipulation library
 *
 * @author duangsuse
 * @version 1.2
 * @see "https://github.com/duangsuse/attrtools"
 * @see "e2immutable.c"
 * @since 1.0
 */
public class Ext2Attr implements Closeable {
    /**
     * Shell for android apps
     */
    @SuppressWarnings("WeakerAccess")
    public static Ext2Attr e2;

    /**
     * Command to execute format
     */
    private static final String command_fmt = "%1$s %2$s %3$s;printf $?_";

    /**
     * Exception string
     */
    private static final String parse_error_msg = "Cannot parse status";

    /**
     * Latest error string field
     */
    @SuppressWarnings("WeakerAccess")
    public static String error = "";

    /**
     * E2IM executable path
     */
    @SuppressWarnings("WeakerAccess")
    public String lib_path = "libe2im.so";

    /**
     * Shell executable path
     */
    @SuppressWarnings("WeakerAccess")
    public String su_path = "su";

    /**
     * Shell process instance
     */
    @SuppressWarnings("WeakerAccess")
    public Process shell = null;

    /**
     * Shell stdin
     */
    @SuppressWarnings("WeakerAccess")
    public PrintStream stdin = null;

    /**
     * Shell stdout
     */
    @SuppressWarnings("WeakerAccess")
    public Scanner stdout = null;

    /**
     * Shell stderr
     */
    @SuppressWarnings("WeakerAccess")
    public Scanner stderr = null;

    /**
     * Default constructor
     *
     * @param su_path  Super User binary path
     * @param lib_path e2im program path
     */
    @SuppressWarnings("unused")
    public Ext2Attr(String su_path, String lib_path) {
        this.su_path = su_path;
        this.lib_path = lib_path;
        new Thread(this::connect).start();
    }

    /**
     * Gets android app libe2im native library path
     *
     * @param ctx app context
     * @return full path of executable
     */
    @SuppressWarnings("WeakerAccess")
    public static String getExecPath(Context ctx) {
        return ctx.getApplicationInfo().nativeLibraryDir + "/libe2im.so";
    }

    /**
     * Map native error message to android translated text
     *
     * @param ctx     app context
     * @param message text to translate
     * @return translated text
     */
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
     * This dialog won't colsed until root shell is created
     *
     * @param ctx app context
     * @param fn  function to execute after shell created
     */
    public static void acquireRoot(Context ctx, Runnable fn) {
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

        e2 = new Ext2Attr(getExecPath(ctx));

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // noinspection StatementWithEmptyBody
                while (e2.not_connected());
                a.cancel();
            }
        }, 100);
    }

    /**
     * Construct a new instance using custom executable
     *
     * @param lib_path e2im executable library path
     */
    @SuppressWarnings("WeakerAccess")
    public Ext2Attr(String lib_path) {
        this.lib_path = lib_path;
        new Thread(this::connect).start();
    }

    /**
     * Use this constructor to avoid <code>connect()</code> when started
     */
    @SuppressWarnings("unused")
    public Ext2Attr() {
    }

    /**
     * Disconnect form shell
     */
    @Override
    public void close() {
        stdin.println("exit");
        stdin.flush();
        try {
            shell.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Connect with shell
     */
    @SuppressWarnings("WeakerAccess")
    public void connect() {
        try {
            shell = Runtime.getRuntime().exec(this.su_path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (shell == null)
            return;

        stdin = new PrintStream(shell.getOutputStream());
        stdout = new Scanner(new DataInputStream(shell.getInputStream()));
        stderr = new Scanner(new DataInputStream(shell.getErrorStream()));
        stdout.useDelimiter("_");
    }

    /**
     * Not connected with shell?
     *
     * @return is not connected with root shell
     */
    @SuppressWarnings("WeakerAccess")
    public boolean not_connected() {
        return shell == null || stdin == null || !isAlive(shell);
    }

    /**
     * Is a process alive?
     *
     * @param p target process
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
     * @param path path to qurey, must be standard file
     *
     * @return 0 for no attribute<p>
     * 1 for +i<p>
     * 2 for +a<p>
     * 3 for +i+a<p>
     * -1 for no file
     * @throws RuntimeException reading attr fails
     */
    public byte query(String path) throws RuntimeException {
        String command = String.format(command_fmt, lib_path, '@', path);
        stdin.println(command);
        stdin.flush();

        switch (stdout.nextInt()) {
            case 0:
                return 0;
            case 255:
                return -1;
            case 254:
                error = stderr.nextLine();
                throw new RuntimeException(error);
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            default:
                throw new RuntimeException(parse_error_msg);
        }
    }

    /**
     * File attribute +i
     *
     * @param path path to add immutable, must be standard file
     *
     * @return 0 for changed<p>
     * 1 for unchanged<p>
     * -1 for no file
     * @throws RuntimeException reading or changing attr fails
     */
    public byte addi(String path) throws RuntimeException {
        String command = String.format(command_fmt, lib_path, '+', path);
        stdin.println(command);
        stdin.flush();

        switch (stdout.nextInt()) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 255:
                return -1;
            case 254:
                error = stderr.nextLine();
                throw new RuntimeException(error);
            default:
                throw new RuntimeException(parse_error_msg);
        }
    }

    /**
     * File attribute -i
     *
     * @param path path to qurey, must be standard file
     *
     * @return 0 for changed<p>
     * 1 for unchanged<p>
     * -1 for no file
     * @throws RuntimeException reading or changing attr fails
     */
    public byte subi(String path) throws RuntimeException {
        String command = String.format(command_fmt, lib_path, '-', path);
        stdin.println(command);
        stdin.flush();

        switch (stdout.nextInt()) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 255:
                return -1;
            case 254:
                error = stderr.nextLine();
                throw new RuntimeException(error);
            default:
                throw new RuntimeException(parse_error_msg);
        }
    }
}
