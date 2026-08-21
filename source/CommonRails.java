import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * CommonRails - Shared output utilities for Tiamat system.
 * Loads print configuration from source/configuration/print-method.xml.
 * Uses grayscale fade-in printing (dark grey -> white) with ANSI 256-color codes.
 */
public class CommonRails
{
    private static final int LINE_WIDTH = 120;
    private static final String RESET = "\u001B[0m";

    // 10x10 square progress config
    private static int SQUARE_SIZE = 10;
    private static String SQUARE_FILLED_CHAR = "\u2588";  // █ full block
    private static String SQUARE_EMPTY_CHAR = "\u2588";   // █ full block (white colored)
    private static final String SQUARE_FILLED_ESC = "\u001b[38;5;208m";  // orange
    private static final String SQUARE_EMPTY_ESC = "\u001b[38;5;255m";   // white
    private static int SQUARE_TARGET_MS = 2000;  // target fill time for ~1MB download

    // Configurable from print-method.xml
    private static String PREFIX = "-- : ";
    private static String OID_LABEL = "Object ID";
    private static String OID_FORMAT = "%010d";
    private static String DATE_LABEL = "Date";
    private static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss z";
    private static String DATE_TIMEZONE = "America/New_York";
    private static String CURRENT_LABEL = "Current";
    private static String CURRENT_PREFIX = "@";
    private static int PAD_WIDTH = 39;
    private static String DECORATOR_START = ".";
    private static String DECORATOR_END = ".";
    private static int FADE_STEPS = 20;
    private static int FADE_DELAY_MS = 20;
    private static int POST_FADE_DELAY_MS = 200;
    private static boolean COLORED_OUTPUT = true;
    private static String OID_FORMAT_ZERO = "0000000000";

    // Tracks active download/worker threads
    private static final AtomicInteger activeThreads = new AtomicInteger(0);
    private static volatile boolean pauseAfterDownload = false;
    private static volatile String lastDownloadLabel = "";

    static { loadConfig(); }

    private static void loadConfig()
    {
        try
        {
            // Try to find print-method.xml relative to working directory
            Path xmlPath = Paths.get("source", "configuration", "print-method.xml");
            if (!Files.exists(xmlPath))
                xmlPath = Paths.get("configuration", "print-method.xml");
            if (!Files.exists(xmlPath))
                return;

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlPath.toFile());
            doc.getDocumentElement().normalize();

            NodeList blocks = doc.getElementsByTagName("block");
            for (int i = 0; i < blocks.getLength(); i++)
            {
                Element el = (Element) blocks.item(i);
                String name = el.getAttribute("name");
                switch (name)
                {
                    case "Prefix" -> PREFIX = text(el, "value", PREFIX);
                    case "ObjectId" -> {
                        OID_LABEL = text(el, "label", OID_LABEL);
                        OID_FORMAT = text(el, "format", OID_FORMAT);
                    }
                    case "Date" -> {
                        DATE_LABEL = text(el, "label", DATE_LABEL);
                        DATE_FORMAT = text(el, "format", DATE_FORMAT);
                        DATE_TIMEZONE = text(el, "timezone", DATE_TIMEZONE);
                    }
                    case "Current" -> {
                        CURRENT_LABEL = text(el, "label", CURRENT_LABEL);
                        CURRENT_PREFIX = text(el, "prefix", CURRENT_PREFIX);
                        PAD_WIDTH = Integer.parseInt(text(el, "pad-width", String.valueOf(PAD_WIDTH)));
                    }
                    case "Message" -> {
                        DECORATOR_START = text(el, "decorator-start", DECORATOR_START);
                        DECORATOR_END = text(el, "decorator-end", DECORATOR_END);
                    }
                }
            }

            NodeList grace = doc.getElementsByTagName("grace");
            if (grace.getLength() > 0)
            {
                Element g = (Element) grace.item(0);
                FADE_STEPS = Integer.parseInt(text(g, "fade-steps", String.valueOf(FADE_STEPS)));
                FADE_DELAY_MS = Integer.parseInt(text(g, "fade-delay-ms", String.valueOf(FADE_DELAY_MS)));
                POST_FADE_DELAY_MS = Integer.parseInt(text(g, "post-fade-delay-ms", String.valueOf(POST_FADE_DELAY_MS)));
            }

            NodeList control = doc.getElementsByTagName("control");
            if (control.getLength() > 0)
            {
                Element c = (Element) control.item(0);
                COLORED_OUTPUT = Boolean.parseBoolean(text(c, "colored-output", "true"));
            }

            NodeList square = doc.getElementsByTagName("square-progress");
            if (square.getLength() > 0)
            {
                Element s = (Element) square.item(0);
                SQUARE_SIZE = Integer.parseInt(text(s, "size", String.valueOf(SQUARE_SIZE)));
                SQUARE_FILLED_CHAR = text(s, "filled-char", SQUARE_FILLED_CHAR);
                SQUARE_EMPTY_CHAR = text(s, "empty-char", SQUARE_EMPTY_CHAR);
                // Colors are hardcoded as actual escape sequences — XML cannot store them
                SQUARE_TARGET_MS = Integer.parseInt(text(s, "target-fill-ms", String.valueOf(SQUARE_TARGET_MS)));
            }
        }
        catch (Exception ignored) {}
    }

    private static String text(Element el, String tag, String def)
    {
        NodeList nl = el.getElementsByTagName(tag);
        if (nl.getLength() == 0) return def;
        String v = nl.item(0).getTextContent().trim();
        return v.isEmpty() ? def : v;
    }

    public static void incrementActiveThreads() { activeThreads.incrementAndGet(); }
    public static void decrementActiveThreads() { activeThreads.decrementAndGet(); }
    public static int getActiveThreadCount() { return activeThreads.get(); }

    /**
     * Grayscale fade-in print: dark grey -> full white using ANSI 256-color codes 236..255.
     * Uses \r to overwrite in place, then prints final line once.
     */
    public static void delayableFinePrinter(final String text, final int delay)
    {
        if (!COLORED_OUTPUT)
        {
            System.out.print("\r" + text + "\r");
            System.out.flush();
            return;
        }

        int[] codes = new int[FADE_STEPS];
        for (int k = 0; k < FADE_STEPS; k++) codes[k] = 236 + k;

        try
        {
            for (int color : codes)
            {
                System.out.print("\r\033[38;5;" + color + "m" + text + RESET);
                System.out.flush();
                Thread.sleep(delay > 0 ? delay : FADE_DELAY_MS);
            }
            System.out.print("\r" + RESET + text + "\n");
            System.out.flush();
        }
        catch (Exception e)
        {
            System.out.print("\r" + RESET + text + "\n");
            System.out.flush();
        }
    }

    /**
     * Called when a download finishes.
     */
    public static void notifyDownloadComplete(String label)
    {
        lastDownloadLabel = label;
        pauseAfterDownload = true;
        printThreadStatus();
    }

    public static void resumeOutput()
    {
        pauseAfterDownload = false;
    }

    public static void printProgressBar(int percent)
    {
        printProgressBar(percent, "");
    }

    public static void printProgressBar(int percent, String label)
    {
        printProgressBar(percent, label, null);
    }

    public static void printProgressBar(int percent, String label, Object owner)
    {
        if (pauseAfterDownload)
        {
            printThreadStatus();
            return;
        }

        int clamped = Math.max(0, Math.min(100, percent));
        int totalCells = SQUARE_SIZE * SQUARE_SIZE;
        int filledCells = (clamped * totalCells) / 100;

        // Build 10x10 square as a single-line compact representation
        // Each row is 10 block chars separated by pipe, fill order: bottom-right → left, then up
        // Rendered as: [row0|row1|row2|...|row9] where each row is 10 chars
        StringBuilder square = new StringBuilder();
        square.append("[");
        for (int displayRow = 0; displayRow < SQUARE_SIZE; displayRow++)
        {
            for (int displayCol = 0; displayCol < SQUARE_SIZE; displayCol++)
            {
                int fillRow = (SQUARE_SIZE - 1) - displayRow;
                int fillCol = (SQUARE_SIZE - 1) - displayCol;
                int fillIndex = fillRow * SQUARE_SIZE + fillCol;

                if (fillIndex < filledCells)
                    square.append(SQUARE_FILLED_ESC).append(SQUARE_FILLED_CHAR).append(RESET);
                else
                    square.append(SQUARE_EMPTY_ESC).append(SQUARE_EMPTY_CHAR).append(RESET);
            }
            if (displayRow < SQUARE_SIZE - 1)
                square.append("|");
        }
        square.append("]");

        String labelStr = (label != null && !label.isEmpty()) ? " " + label : "";

        // Build component-style prefix
        String simple = owner != null
            ? (owner instanceof Class<?> c ? c.getSimpleName() : owner.getClass().getSimpleName())
            : "SearchEngineClient";
        String hashStr = owner != null ? String.format(OID_FORMAT, owner.hashCode()) : OID_FORMAT_ZERO;
        String oidColor = "\u001b[38;5;208m";

        SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT);
        fmt.setTimeZone(TimeZone.getTimeZone(DATE_TIMEZONE));
        String ts = fmt.format(new Date());
        String padded = padClassname(simple);

        String line = PREFIX + "[" + OID_LABEL + ": " + oidColor + hashStr + RESET + "] ["
            + DATE_LABEL + ": " + ts + "] " + padded + " " + square + labelStr;

        System.out.print("\r" + line);
        System.out.flush();
    }

    // =========================================================================
    // 10x10 SQUARE PROGRESS INDICATOR
    // Fills from bottom-right to left, then up one row at a time.
    // 100 cells = 100%. Target fill: ≤2 seconds for a ~1MB image download.
    // =========================================================================

    /**
     * Renders a 10x10 grid progress indicator (standalone multi-line version).
     * Fill order: bottom-right → left across row, then up to next row.
     * Each cell = 1%. At 100%, the full square is filled orange on white.
     *
     * Uses ANSI cursor movement to render multi-line in-place.
     *
     * @param percent 0-100 completion
     * @param label   optional label to print above the square
     */
    public static void printSquareProgress(int percent, String label)
    {
        int clamped = Math.max(0, Math.min(100, percent));
        int totalCells = SQUARE_SIZE * SQUARE_SIZE;
        int filledCells = (clamped * totalCells) / 100;

        StringBuilder output = new StringBuilder();

        if (label != null && !label.isEmpty())
            output.append("  ").append(label).append(" ").append(clamped).append("%\n");

        for (int displayRow = 0; displayRow < SQUARE_SIZE; displayRow++)
        {
            output.append("  ");
            for (int displayCol = 0; displayCol < SQUARE_SIZE; displayCol++)
            {
                int fillRow = (SQUARE_SIZE - 1) - displayRow;
                int fillCol = (SQUARE_SIZE - 1) - displayCol;
                int fillIndex = fillRow * SQUARE_SIZE + fillCol;

                if (fillIndex < filledCells)
                    output.append(SQUARE_FILLED_ESC).append(SQUARE_FILLED_CHAR).append(RESET);
                else
                    output.append(SQUARE_EMPTY_ESC).append(SQUARE_EMPTY_CHAR).append(RESET);
            }
            output.append("\n");
        }

        // Move cursor up to overwrite on next call (size + 1 for label line)
        int linesUp = SQUARE_SIZE + (label != null && !label.isEmpty() ? 1 : 0);
        output.append("\033[").append(linesUp).append("A");

        System.out.print(output);
        System.out.flush();
    }

    /**
     * Finalizes the square progress display — prints the completed grid
     * without the cursor-up escape so subsequent output flows normally.
     */
    public static void finalizeSquareProgress(String label)
    {
        StringBuilder output = new StringBuilder();

        if (label != null && !label.isEmpty())
            output.append("  ").append(label).append(" 100%\n");

        for (int displayRow = 0; displayRow < SQUARE_SIZE; displayRow++)
        {
            output.append("  ");
            for (int displayCol = 0; displayCol < SQUARE_SIZE; displayCol++)
                output.append(SQUARE_FILLED_ESC).append(SQUARE_FILLED_CHAR).append(RESET);
            output.append("\n");
        }

        System.out.print(output);
        System.out.flush();
    }

    /**
     * Returns the target fill time in milliseconds for the square progress indicator.
     * Configured via print-method.xml <square-progress><target-fill-ms>.
     * Default: 2000ms (should fill in ≤2 seconds for ~1MB downloads).
     */
    public static int getSquareTargetMs() { return SQUARE_TARGET_MS; }

    /**
     * Returns the per-cell delay in ms to hit the target fill time.
     * For a 10x10 grid (100 cells) at 2000ms target = 20ms per cell.
     */
    public static int getSquareCellDelayMs()
    {
        int totalCells = SQUARE_SIZE * SQUARE_SIZE;
        return Math.max(1, SQUARE_TARGET_MS / totalCells);
    }

    public static void printThreadStatus()
    {
        String status = "    [PAUSED] Last download: " + lastDownloadLabel
            + " | Active threads: " + activeThreads.get();
        StringBuilder sb = new StringBuilder(status);
        while (sb.length() < LINE_WIDTH) sb.append(' ');
        System.out.print("\r" + sb.toString());
    }

    /**
     * Component-style print with object ID, timestamp, padded class name, and fade-in.
     * Format: -- : [Object ID: XXXXXXXXXX] [Date: ...] [Current: @ClassName     ] . message .
     */
    public static void printSystemComponent(Object owner, int hash, String line)
    {
        String simple = owner instanceof Class<?> c ? c.getSimpleName() : owner.getClass().getSimpleName();
        String hashStr = String.format(OID_FORMAT, hash);
        String oidColor = "\u001b[38;5;208m";

        SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT);
        fmt.setTimeZone(TimeZone.getTimeZone(DATE_TIMEZONE));
        String ts = fmt.format(new Date());

        String padded = padClassname(simple);

        // Apply decorators
        String msg = DECORATOR_START + " " + line + " " + DECORATOR_END;

        String ref = PREFIX + "[" + OID_LABEL + ": " + oidColor + hashStr + RESET + "] ["
            + DATE_LABEL + ": " + ts + "] " + padded + " " + msg;
        delayableFinePrinter(ref, FADE_DELAY_MS);
    }

    private static String padClassname(String name)
    {
        String inner = CURRENT_LABEL + ": " + CURRENT_PREFIX + name;
        int pad = Math.max(0, PAD_WIDTH - inner.length());
        return "[" + inner + " ".repeat(pad) + "]";
    }

    public static void println(String message)
    {
        delayableFinePrinter(message, FADE_DELAY_MS);
    }

    public static void println()
    {
        System.out.println();
    }

    public static void printError(String message)
    {
        System.err.println("\u001b[31m" + message + "\u001b[0m");
    }
}
