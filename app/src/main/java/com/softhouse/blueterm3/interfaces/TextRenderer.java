package com.softhouse.blueterm3.interfaces;

import android.graphics.Canvas;

/**
 * Text renderer interface
 */


public interface TextRenderer {
    int getCharacterWidth();

    int getCharacterHeight();

    void drawTextRun(Canvas canvas, float x, float y,
                     int lineOffset, char[] text,
                     int index, int count, boolean cursor, int foreColor, int backColor);
}