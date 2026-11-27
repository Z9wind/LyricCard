package com.dsh.lyriccard.widget;


import android.annotation.RemotableViewMethod;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.RemoteViews;
import android.widget.TextView;

/**
 * 支持在 AppWidget 里动态调整阴影的 TextView。
 *
 * 系统 TextView 只有 setShadowLayer(float, float, float, int) 这种多参数方法，
 * RemoteViews 只能调用单参数的 @RemotableViewMethod，所以这里包一层 setShadowLevel(int)。
 */
@RemoteViews.RemoteView
public class ShadowTextView extends TextView {
    public ShadowTextView(Context context) { super(context); }

    public ShadowTextView(Context context, AttributeSet attrs) { super(context, attrs); }

    public ShadowTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** level 0~100：0 无阴影，100 最深。 */
    @RemotableViewMethod
    public void setShadowLevel(int level) {
        int l = Math.max(0, Math.min(100, level));
        if (l == 0) {
            setShadowLayer(0f, 0f, 0f, 0);
            return;
        }
        float radius = 0.5f + 2.5f * l / 100f;
        int alpha = l * 255 / 100;
        setShadowLayer(radius, 0f, 1f, alpha << 24);
    }
}
