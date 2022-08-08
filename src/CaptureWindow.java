/*
 * CaptureWindow.java
 *  Save an image of a Window to a file. Window is selected based on title.
 *  Window title, output location, and output format are read from the command line.
 *  
 * @author: Bill Thompson
 * @license: GPL 3
 * @copyright: 2022-08-08
 * 
 * @requires MS Windows 10, 11
 * @requires jna-5.12.1.jar, jna-platform-5.12.1.jar from https://github.com/java-native-access/jna
 * 
 * Based on
 * https://stackoverflow.com/questions/3188484/windows-how-to-get-a-list-of-all-visible-windows/3238193#3238193
 */

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.platform.win32.WinDef;

import java.io.File;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;

public class CaptureWindow {
    /*
     * WindowInfo
     * A class to store window information.
     */
    private static class WindowInfo {
        public int hwnd;
        public WinDef.RECT rect;
        public String title;

        /* Constructors */
        public WindowInfo() {
            this.hwnd = 0;
            this.rect = new WinDef.RECT();
            this.title = null;
        }

        public WindowInfo(int hwnd, WinDef.RECT rect, String title) {
            this.hwnd = hwnd;
            this.rect = rect;
            this.title = title;
        }

        /* for printing */
        public String toString() {
            return String.format("(%d,%d)-(%d,%d) : \"%s\"",
                    rect.left, rect.top,
                    rect.right, rect.bottom,
                    title);
        }
    }

    /*
     * Interface for callback for enumerating windows.
     */
    private static interface WndEnumProc extends StdCallLibrary.StdCallCallback {
        boolean callback(int hWnd, int lParam);
    }

    /*
     * User32 functions interface
     */
    private static interface User32 extends StdCallLibrary {
        final User32 instance = (User32) Native.load("user32", User32.class);

        boolean EnumWindows(WndEnumProc wndenumproc, int lParam);

        boolean IsWindowVisible(int hWnd);

        int GetWindowRect(int hWnd, WinDef.RECT r);

        void GetWindowTextA(int hWnd, byte[] buffer, int buflen);

        int GetTopWindow(int hWnd);

        int GetWindow(int hWnd, int flag);
    }

    /**
     * getOutputFileName
     * Construct an output file name from outPath and current date and time.
     * 
     * @param outPath - where output should be written
     * @param format  - outfile imgae type
     * @return full path output file based on data nad time.
     */
    private static String getOutputFileName(String outPath, String format) {
        outPath = outPath.replace('\\', '/');
        if (outPath.charAt(outPath.length() - 1) != '/')
            outPath += "/";

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        LocalDateTime now = LocalDateTime.now();
        String nowStr = dtf.format(now);

        String outFileName = outPath + nowStr + "." + format;

        return outFileName;
    }

    /**
     * captureWindow
     * Capture MS Windows window as image and save it to a file.
     * 
     * @param targetWin - a WindowInfo object reference. Window to capture
     * @param format    - output format, png, jpg, etc.
     * @param fileName  - where output should go
     */
    private static void captureWindow(WindowInfo targetWin, String format, String fileName) {
        try {
            Robot robot = new Robot();

            Rectangle winRect = new Rectangle(targetWin.rect.left, targetWin.rect.top,
                    Math.abs(targetWin.rect.right - targetWin.rect.left + 1),
                    Math.abs(targetWin.rect.bottom - targetWin.rect.top + 1));
            BufferedImage screenImage = robot.createScreenCapture(winRect);
            ImageIO.write(screenImage, format, new File(fileName));

            System.out.println("Screenshot saved!");
        } catch (AWTException | IOException ex) {
            System.err.println(ex);
        }
    }

    public static void main(String[] args) {
        /* get window title, output path, and image format from command line. */
        final String target = args[0];
        final String outputPath = args[1];
        final String format = args[2];

        /* contruct file name */
        final String outFileName = getOutputFileName(outputPath, format);

        // create an empty WindowInfo object to hold target window when we find it.
        final WindowInfo targetWin = new WindowInfo();

        // enumerate windows, stop when we find target
        User32.instance.EnumWindows(new WndEnumProc() {
            public boolean callback(int hWnd, int lParam) {
                if (User32.instance.IsWindowVisible(hWnd)) {
                    final WinDef.RECT r = new WinDef.RECT();
                    User32.instance.GetWindowRect(hWnd, r);
                    if (r.left > -32000) { // If it's not minimized
                        byte[] buffer = new byte[1024];
                        User32.instance.GetWindowTextA(hWnd, buffer, buffer.length);
                        String title = Native.toString(buffer);
                        // If we found it, get the window information
                        if (title.indexOf(target) != -1) {
                            targetWin.hwnd = hWnd;
                            targetWin.rect = r;
                            targetWin.title = title;
                            return false;
                        }
                    }
                }
                return true;
            }
        }, 0);

        System.out.println(targetWin);

        captureWindow(targetWin, format, outFileName);
    }
}
