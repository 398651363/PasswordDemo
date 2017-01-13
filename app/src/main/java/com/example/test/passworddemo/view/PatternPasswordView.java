package com.example.test.passworddemo.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.example.test.passworddemo.R;

import java.util.List;

/**
 * Created by liuzhaohui on 2017/1/12.
 */
public class PatternPasswordView extends View {
    private final String TAG = "PatternPasswordView";
    private Context mContext;
    private static final int ASPECT_SQUARE = 0; //视图的最小高度和宽度
    private static final int ASPECT_LOCK_WIDTH = 1; //固定宽度,高度将最取(w h)的较小值
    private static final int ASPECT_LOCK_HEIGHT = 2; //固定高度,宽度将最取(w h)的较小值
    private int mAspect;
    private int mBitmapWidth;   //图案单元格的宽度
    private int mBitmapHeight;  //图案单元格的高度
    private final int mStrokeAlpha = 128;   //图案轨迹的透明度

    private boolean mIsPatternVisible;  //绘制的图案轨迹是否可见
    private boolean mEnableHapticFeedback = true;   //是否开启了触感反馈

    private Paint mPaint = new Paint();
    private Paint mPathPaint = new Paint(); //图案轨迹绘制画笔

    private Bitmap mBitmapCircleDefault;     //图案单元格的默认图片
    private Bitmap mBitmapCircleRight;       //图案单元格的正确图片
    private Bitmap mBitmapCircleWrong;         //图案单元格的错误图片

    private OnPatternListener mOnPatternListener;   //图案绘制的监听器

    /**
     * 主要用于显示3 * 3矩阵的单元格
     */
    public static final class Cell {
        final int mRow;
        final int mColumn;

        //保证图案的单元格数为9
        private static final Cell[][] mCell = createCells();

        private Cell(int row, int column) {
            mRow = row;
            mColumn =column;
        }

        private static Cell[][] createCells() {
            Cell[][] cell = new Cell[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    cell[i][j] = new Cell(i, j);
                }
            }
            return cell;
        }

        public int getRow() {
            return mRow;
        }

        public int getColumn() {
            return mColumn;
        }

        public static synchronized Cell getCellOfLoc(int row, int column) {
            checkValidRange(row, column);
            return mCell[row][column];
        }

        private static void checkValidRange(int row, int column) {
            if (row < 0 || row > 2) {
                throw new IllegalArgumentException("row must be in range 0-2");
            }
            if (column < 0 || column > 2) {
                throw new IllegalArgumentException("column must be in range 0-2");
            }
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("[ row = ").append(mRow).append(" , column = ").append(mColumn);
            return stringBuilder.toString();
        }
    }

    /**
     * 绘图类型：正确、错误、动画（供调试用）
     */
    public enum DisplayMode {
        CORRECT, ANIMATE, WRONG;
    }

    /**
     * 绘图回调接口
     */
    public interface OnPatternListener {
        /**
         * 开始绘制图案
         */
        void onPatternStart();

        /**
         * 图案被清掉
         */
        void onPatternCleared();

        /**
         * 单元格添加完毕
         */
        void onPatternCellAdded(List<Cell> pattern);

        /**
         * 图案绘制完成
         */
        void onPatternDetected(List<Cell> pattern);
    }

    public PatternPasswordView(Context context) {
        super(context, null);
    }

    public PatternPasswordView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;

        TypedArray typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.PatternPasswordView);
        final String aspect = typedArray.getString(R.styleable.PatternPasswordView_aspect);
        if ("square".equals(aspect)) {
            mAspect = ASPECT_SQUARE;
        } else if ("lock_width".equals(aspect)) {
            mAspect = ASPECT_LOCK_WIDTH;
        } else if ("lock_height".equals(aspect)) {
            mAspect = ASPECT_LOCK_HEIGHT;
        } else {
            mAspect = ASPECT_SQUARE;
        }

        setClickable(true);

        mPathPaint.setAntiAlias(true);  //设置图案轨迹画笔抗锯齿
        mPathPaint.setDither(true);//设置图案轨迹画笔防抖动

        mIsPatternVisible = true;   //图案轨迹是否可见，默认为true，需要从数据库中读取
        if (!mIsPatternVisible) {
            mPathPaint.setColor(R.color.transparent_path_color);
        } else {
            mPathPaint.setColor(R.color.opaque_path_color);
        }
        mPathPaint.setAlpha(mStrokeAlpha);  //设置画笔透明度
        mPathPaint.setStyle(Paint.Style.STROKE);    //设置画笔样式为空心
        mPathPaint.setStrokeJoin(Paint.Join.ROUND); //设置画笔结合处的形状为圆角
        mPathPaint.setStrokeCap(Paint.Cap.ROUND); //在画笔结尾处追加一个半圆

        mBitmapCircleDefault = getBitmapFor(R.mipmap.gesture_pattern_item_normal_bg);
        mBitmapCircleRight = getBitmapFor(R.mipmap.gesture_pattern_selected_right);
        mBitmapCircleWrong = getBitmapFor(R.mipmap.gesture_pattern_selected_wrong);

        final Bitmap bitmaps[] = {mBitmapCircleDefault, mBitmapCircleRight, mBitmapCircleWrong};
        for (Bitmap bitmap : bitmaps) {
            mBitmapWidth = Math.max(mBitmapWidth, bitmap.getWidth());
            mBitmapHeight = Math.max(mBitmapHeight, bitmap.getHeight());
        }

        typedArray.recycle();
    }

    private Bitmap getBitmapFor(int resId) {
        return BitmapFactory.decodeResource(getContext().getResources(), resId);
    }

    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mEnableHapticFeedback = tactileFeedbackEnabled;
    }

    public void setOnPatternListener(OnPatternListener onPatternListener) {
        mOnPatternListener = onPatternListener;
    }

    /**
     * 稍后添加
     * @param displayMode
     * @param pattern
     */
    public void setPattern(DisplayMode displayMode, List<Cell> pattern) {

    }

    /**
     * 稍后添加
     * @param displayMode
     */
    public void setDisplayMode(DisplayMode displayMode) {

    }

    /**
     * 稍后添加，单元格已添加通知事件
     */
    private void notifyCellAdded() {

    }

    /**
     * 稍后添加，图案开始绘制通知事件
     */
    private void notifyPatternStarted() {

    }

    /**
     * 稍后添加，图案绘制完成通知事件
     */
    private void notifyPatternDetected() {

    }

    /**
     * 稍后添加，图案被清除通知事件
     */
    private void notifyPatternCleared() {

    }

    /**
     * 稍后添加，清除图案公共接口
     */
    public void clearPattern() {

    }

    @Override
    protected int getSuggestedMinimumWidth() {
        return 3 * mBitmapWidth;
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        return 3 * mBitmapHeight;
    }

    private int resolveMeasured(int measureSpec, int desired) {
        int result = 0;
        int specSize = MeasureSpec.getSize(measureSpec);
        int mode = MeasureSpec.getMode(measureSpec);
        switch (mode) {
            case MeasureSpec.UNSPECIFIED:   //未指定尺寸，这种情况不多，一般都是父控件是AdapterView，通过measure方法传入的模式
                result = desired;
                break;
            case MeasureSpec.AT_MOST:   //最大尺寸，当控件的layout_width或layout_height指定为WRAP_CONTENT时，控件大小一般随着控件的子空间或内容进行变化，
                result = Math.max(specSize, desired);               // 此时控件尺寸只要不超过父控件允许的最大尺寸即可。因此，此时的mode是AT_MOST，size给出了父控件允许的最大尺寸;
                break;
            case MeasureSpec.EXACTLY:   //精确尺寸，当我们将控件的layout_width或layout_height指定为具体数值时
            default:                     // 如andorid:layout_width="50dip"，或者为FILL_PARENT是，都是控件大小已经确定的情况，都是精确尺寸
                result = specSize;
        }
        return result;
    }

    /**
     * widthMeasureSpec和heightMeasureSpec不是一般的尺寸数值，而是将模式和尺寸组合在一起的数值。
     * 我们需要通过int mode = MeasureSpec.getMode(widthMeasureSpec)得到模式，
     * 用int size = MeasureSpec.getSize(widthMeasureSpec)得到尺寸
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minWidth = getSuggestedMinimumWidth();
        final int minHeight = getSuggestedMinimumHeight();
        int viewWidth = resolveMeasured(widthMeasureSpec, minWidth);
        int viewHeight = resolveMeasured(heightMeasureSpec, minHeight);

        switch (mAspect) {
            case ASPECT_SQUARE:
                viewWidth = viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case ASPECT_LOCK_WIDTH:
                viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case ASPECT_LOCK_HEIGHT:
                viewWidth = Math.min(viewWidth, viewHeight);
                break;
        }

        //在覆写onMeasure方法的时候，必须调用 setMeasuredDimension(int,int)来存储这个View经过测量得到的measured width and height。
        // 如果没有这么做，将会由measure(int, int)方法抛出一个IllegalStateException。
        setMeasuredDimension(viewWidth, viewHeight);
    }
}
