package com.termux.terminal;

/** An application's explicit focused text-input bounds, in zero-based terminal cells. */
public final class TextInputRegion {
    public static final TextInputRegion EMPTY = new TextInputRegion(0, 0, 0, 0);

    private final int x, y, width, height;

    private TextInputRegion(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Decode OSC 777;input-region;x;y;width;height at the terminal boundary. */
    static TextInputRegion fromOsc(String parameter, int columns, int rows) {
        String[] fields = parameter.split(";", -1);
        if (fields.length != 5 || !fields[0].equals("input-region"))
            throw new IllegalArgumentException("Expected input-region;x;y;width;height");
        int x = Integer.parseInt(fields[1]);
        int y = Integer.parseInt(fields[2]);
        int width = Integer.parseInt(fields[3]);
        int height = Integer.parseInt(fields[4]);
        if (x < 0 || y < 0 || x > columns || y > rows || width < 0 || height < 0
            || width > columns - x || height > rows - y)
            throw new IllegalArgumentException("Input region lies outside the terminal");
        return new TextInputRegion(x, y, width, height);
    }

    public boolean contains(int column, int row) {
        return column >= x && row >= y && column - x < width && row - y < height;
    }
}
