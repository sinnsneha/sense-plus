package com.senseplus.sense.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.senseplus.sense.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Equal-height tile grid (tokens.layout.grid): 2 columns on phones, 3 from 600dp, 14dp
 * gaps. GONE children are skipped and the grid re-flows, so Home stays tidy when the
 * profile hides tools. A child with {@code app:layout_fullSpan="true"} takes a whole row,
 * and a lone last tile spans its row too. Rows grow with their tallest tile, so text is
 * never clipped at large font scales.
 */
public class TileGridLayout extends ViewGroup {

    private final int gap;
    private int[] xs = new int[0];
    private int[] ys = new int[0];
    private final List<View> row = new ArrayList<>();

    public TileGridLayout(Context context) {
        this(context, null);
    }

    public TileGridLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gap = getResources().getDimensionPixelSize(R.dimen.gap_cards);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int inner = width - getPaddingLeft() - getPaddingRight();
        int columns = width / getResources().getDisplayMetrics().density >= 600 ? 3 : 2;
        int colW = (inner - gap * (columns - 1)) / columns;

        int count = getChildCount();
        if (xs.length != count) {
            xs = new int[count];
            ys = new int[count];
        }
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < count; i++) if (getChildAt(i).getVisibility() != GONE) visible.add(i);
        int n = visible.size();

        int y = getPaddingTop();
        int col = 0, rowMax = 0;
        row.clear();
        for (int v = 0; v < n; v++) {
            int i = visible.get(v);
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            boolean last = v == n - 1;
            boolean full = lp.fullSpan || (last && col == 0 && n > 1);
            if (full && col != 0) {
                stretch(rowMax);
                y += rowMax + gap;
                col = 0;
                rowMax = 0;
            }
            int span = full ? columns : 1;
            int cw = colW * span + gap * (span - 1);
            int childH = lp.height == LayoutParams.MATCH_PARENT ? LayoutParams.WRAP_CONTENT : lp.height;
            child.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    getChildMeasureSpec(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, childH));
            xs[i] = getPaddingLeft() + col * (colW + gap);
            ys[i] = y;
            row.add(child);
            rowMax = Math.max(rowMax, child.getMeasuredHeight());
            col += span;
            if (col >= columns || last) {
                stretch(rowMax);
                y += rowMax;
                if (!last) y += gap;
                col = 0;
                rowMax = 0;
            }
        }
        y += getPaddingBottom();
        setMeasuredDimension(width, resolveSize(y, heightMeasureSpec));
    }

    /** Stretch every tile of the current row to the row height, then start a new row. */
    private void stretch(int rowH) {
        for (View child : row) {
            if (child.getMeasuredHeight() != rowH) {
                child.measure(MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rowH, MeasureSpec.EXACTLY));
            }
        }
        row.clear();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < getChildCount() && i < xs.length; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth(), ch = child.getMeasuredHeight();
            int x = rtl ? width - xs[i] - cw : xs[i];
            child.layout(x, ys[i], x + cw, ys[i] + ch);
        }
    }

    // ---------- layout params ----------

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public boolean fullSpan;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.TileGridLayout_Layout);
            fullSpan = a.getBoolean(R.styleable.TileGridLayout_Layout_layout_fullSpan, false);
            a.recycle();
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }
}
