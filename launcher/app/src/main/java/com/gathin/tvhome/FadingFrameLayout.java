package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class FadingFrameLayout extends FrameLayout {
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float fadePx;
    private boolean fadeTop = false;
    private int lastHeight = -1;

    public FadingFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        fadePx = 52f * context.getResources().getDisplayMetrics().density;
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    public void setFadeTop(boolean fade) {
        if (fadeTop != fade) {
            fadeTop = fade;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!fadeTop || getWidth() <= 0 || getHeight() <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        if (lastHeight != getHeight()) {
            lastHeight = getHeight();
            fadePaint.setShader(new LinearGradient(
                    0f, 0f, 0f, fadePx,
                    0x00000000, 0xFF000000,
                    Shader.TileMode.CLAMP
            ));
        }
        int sc = canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);
        super.dispatchDraw(canvas);
        canvas.drawRect(0f, 0f, getWidth(), fadePx, fadePaint);
        canvas.restoreToCount(sc);
    }
}
