package it.liceoarzignano.bold.external.inkpageindicator;

/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;

import java.util.Arrays;

import it.liceoarzignano.bold.R;

public class InkPageIndicator extends View implements ViewPager.OnPageChangeListener,
        View.OnAttachStateChangeListener {

    // defaults
    private static final int DEFAULT_DOT_SIZE = 8;                      // dp
    private static final int DEFAULT_GAP = 12;                          // dp
    private static final int DEFAULT_ANIM_DURATION = 400;               // ms
    private static final int DEFAULT_UNSELECTED_COLOUR = 0x80ffffff;    // 50% white
    private static final int DEFAULT_SELECTED_COLOUR = 0xffffffff;      // 100% white

    // constants
    private static final float INVALID_FRACTION = -1f;
    private static final float MINIMAL_REVEAL = 0.00001f;

    // configurable attributes
    private final int dotDiameter;
    private final int gap;
    private final long animDuration;

    // derived from attributes
    private final float dotRadius;
    private final float halfDotRadius;
    private final long animHalfDuration;
    // drawing
    private final Paint unselectedPaint;
    private final Paint selectedPaint;
    private final Path combinedUnselectedPath;
    private final Path unselectedDotPath;
    private final Path unselectedDotLeftPath;
    private final Path unselectedDotRightPath;
    private final RectF rectF;
    private final Interpolator interpolator;
    private float dotTopY;
    private float dotCenterY;
    private float dotBottomY;
    // ViewPager
    private ViewPager viewPager;
    // state
    private int pageCount;
    private int currentPage;
    private int previousPage;
    private float selectedDotX;
    private boolean selectedDotInPosition;
    private float[] dotCenterX;
    private float[] joiningFractions;
    private float retreatingJoinX1;
    private float retreatingJoinX2;
    private float[] dotRevealFractions;
    private boolean isAttachedToWindow;
    private boolean pageChanging;
    // animation
    private ValueAnimator moveAnimation;
    private PendingRetreatAnimator retreatAnimation;
    private PendingRevealAnimator[] revealAnimations;

    public InkPageIndicator(Context context) {
        this(context, null, 0);
    }

    public InkPageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InkPageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        int density = (int) context.getResources().getDisplayMetrics().density;

        // Load attributes
        TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.InkPageIndicator, defStyle, 0);

        dotDiameter = a.getDimensionPixelSize(R.styleable.InkPageIndicator_dotDiameter,
                DEFAULT_DOT_SIZE * density);
        dotRadius = dotDiameter / 2;
        halfDotRadius = dotRadius / 2;
        gap = a.getDimensionPixelSize(R.styleable.InkPageIndicator_dotGap,
                DEFAULT_GAP * density);
        animDuration = (long) a.getInteger(R.styleable.InkPageIndicator_animationDuration,
                DEFAULT_ANIM_DURATION);
        animHalfDuration = animDuration / 2;
        int unselectedColour = a.getColor(R.styleable.InkPageIndicator_pageIndicatorColor,
                DEFAULT_UNSELECTED_COLOUR);
        int selectedColour = a.getColor(R.styleable.InkPageIndicator_currentPageIndicatorColor,
                DEFAULT_SELECTED_COLOUR);

        a.recycle();

        unselectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unselectedPaint.setColor(unselectedColour);
        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint.setColor(selectedColour);
        interpolator = new FastOutSlowInInterpolator();

        // create paths & rect now – reuse & rewind later
        combinedUnselectedPath = new Path();
        unselectedDotPath = new Path();
        unselectedDotLeftPath = new Path();
        unselectedDotRightPath = new Path();
        rectF = new RectF();

        addOnAttachStateChangeListener(this);
    }

    public void setViewPager(ViewPager viewPager) {
        this.viewPager = viewPager;
        viewPager.addOnPageChangeListener(this);
        setPageCount(viewPager.getAdapter().getCount());
        viewPager.getAdapter().registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                setPageCount(InkPageIndicator.this.viewPager.getAdapter().getCount());
            }
        });
        setCurrentPageImmediate();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (isAttachedToWindow) {
            float fraction = positionOffset;
            int currentPosition = pageChanging ? previousPage : currentPage;
            int leftDotPosition = position;
            // when swiping from #2 to #1 ViewPager reports position as 1 and a descending offset
            // need to convert this into our left-dot-based 'coordinate space'
            if (currentPosition != position) {
                fraction = 1f - positionOffset;

                // if user scrolls completely to next page then the position param updates to that
                // new page but we're not ready to switch our 'current' page yet so adjust for that
                if (fraction == 1f) {
                    leftDotPosition = Math.min(currentPosition, position);
                }
            }
            setJoiningFraction(leftDotPosition, fraction);
        }
    }

    @Override
    public void onPageSelected(int position) {
        if (isAttachedToWindow) {
            // this is the main event we're interested in!
            setSelectedPage(position);
        } else {
            // when not attached, don't animate the move, just store immediately
            setCurrentPageImmediate();
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    private void setPageCount(int pages) {
        pageCount = pages;
        resetState();
        requestLayout();
    }

    private void calculateDotPositions(int width) {
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = width - getPaddingRight();

        int requiredWidth = getRequiredWidth();
        float startLeft = left + (right - left - requiredWidth) / 2 + dotRadius;

        dotCenterX = new float[pageCount];
        for (int i = 0; i < pageCount; i++) {
            dotCenterX[i] = startLeft + i * (dotDiameter + gap);
        }
        dotTopY = top;
        dotCenterY = top + dotRadius;
        dotBottomY = top + dotDiameter;

        setCurrentPageImmediate();
    }

    private void setCurrentPageImmediate() {
        currentPage = viewPager != null ? viewPager.getCurrentItem() : 0;
        if (dotCenterX != null && dotCenterX.length > 0 &&
                (moveAnimation == null || !moveAnimation.isStarted())) {
            selectedDotX = dotCenterX[currentPage];
        }
    }

    private void resetState() {
        joiningFractions = new float[pageCount - 1];
        Arrays.fill(joiningFractions, 0f);
        dotRevealFractions = new float[pageCount];
        Arrays.fill(dotRevealFractions, 0f);
        retreatingJoinX1 = INVALID_FRACTION;
        retreatingJoinX2 = INVALID_FRACTION;
        selectedDotInPosition = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int desiredHeight = getDesiredHeight();
        int height;
        switch (View.MeasureSpec.getMode(heightMeasureSpec)) {
            case View.MeasureSpec.EXACTLY:
                height = View.MeasureSpec.getSize(heightMeasureSpec);
                break;
            case View.MeasureSpec.AT_MOST:
                height = Math.min(desiredHeight, View.MeasureSpec.getSize(heightMeasureSpec));
                break;
            case View.MeasureSpec.UNSPECIFIED:
            default:
                height = desiredHeight;
                break;
        }

        int desiredWidth = getDesiredWidth();
        int width;
        switch (View.MeasureSpec.getMode(widthMeasureSpec)) {
            case View.MeasureSpec.EXACTLY:
                width = View.MeasureSpec.getSize(widthMeasureSpec);
                break;
            case View.MeasureSpec.AT_MOST:
                width = Math.min(desiredWidth, View.MeasureSpec.getSize(widthMeasureSpec));
                break;
            case View.MeasureSpec.UNSPECIFIED:
            default:
                width = desiredWidth;
                break;
        }
        setMeasuredDimension(width, height);
        calculateDotPositions(width);
    }

    private int getDesiredHeight() {
        return getPaddingTop() + dotDiameter + getPaddingBottom();
    }

    private int getRequiredWidth() {
        return pageCount * dotDiameter + (pageCount - 1) * gap;
    }

    private int getDesiredWidth() {
        return getPaddingLeft() + getRequiredWidth() + getPaddingRight();
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        isAttachedToWindow = true;
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        isAttachedToWindow = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (viewPager == null || pageCount == 0) return;
        drawUnselected(canvas);
        drawSelected(canvas);
    }

    private void drawUnselected(Canvas canvas) {

        combinedUnselectedPath.rewind();

        // draw any settled, revealing or joining dots
        for (int page = 0; page < pageCount; page++) {
            int nextXIndex = page == pageCount - 1 ? page : page + 1;
            Path unselectedPath = getUnselectedPath(page,
                    dotCenterX[page],
                    dotCenterX[nextXIndex],
                    page == pageCount - 1 ? INVALID_FRACTION : joiningFractions[page],
                    dotRevealFractions[page]);
            unselectedPath.addPath(combinedUnselectedPath);
            combinedUnselectedPath.addPath(unselectedPath);
        }
        // draw any retreating joins
        if (retreatingJoinX1 != INVALID_FRACTION) {
            Path retreatingJoinPath = getRetreatingJoinPath();
            combinedUnselectedPath.addPath(retreatingJoinPath);
        }

        canvas.drawPath(combinedUnselectedPath, unselectedPaint);
    }

    /**
     * Unselected dots can be in 6 states:
     * <p>
     * #1 At rest
     * #2 Joining neighbour, still separate
     * #3 Joining neighbour, combined curved
     * #4 Joining neighbour, combined straight
     * #5 Join retreating
     * #6 Dot re-showing / revealing
     * <p>
     * It can also be in a combination of these states e.g. joining one neighbour while
     * retreating from another.  We therefore create a Path so that we can examine each
     * dot pair separately and later take the union for these cases.
     * <p>
     * This function returns a path for the given dot **and any action to it's right** e.g. joining
     * or retreating from it's neighbour
     *
     * @return path
     */
    private Path getUnselectedPath(int page, float centerX, float nextCenterX,
                                   float joiningFraction, float dotRevealFraction) {

        unselectedDotPath.rewind();

        if ((joiningFraction == 0f || joiningFraction == INVALID_FRACTION)
                && dotRevealFraction == 0f
                && !(page == currentPage && selectedDotInPosition)) {

            // case #1 – At rest
            unselectedDotPath.addCircle(dotCenterX[page], dotCenterY, dotRadius, Path.Direction.CW);
        }

        float endX1;
        float endY1;
        float endX2;
        float endY2;
        float controlX1;
        float controlY1;
        float controlX2;
        float controlY2;
        if (joiningFraction > 0f && joiningFraction <= 0.5f
                && retreatingJoinX1 == INVALID_FRACTION) {

            // case #2 – Joining neighbour, still separate

            // start with the left dot
            unselectedDotLeftPath.rewind();

            // start at the bottom center
            unselectedDotLeftPath.moveTo(centerX, dotBottomY);

            // semi circle to the top center
            rectF.set(centerX - dotRadius, dotTopY, centerX + dotRadius, dotBottomY);
            unselectedDotLeftPath.arcTo(rectF, 90, 180, true);

            // cubic to the right middle
            endX1 = centerX + dotRadius + joiningFraction * gap;
            endY1 = dotCenterY;
            controlX1 = centerX + halfDotRadius;
            controlY1 = dotTopY;
            controlX2 = endX1;
            controlY2 = endY1 - halfDotRadius;
            unselectedDotLeftPath.cubicTo(controlX1, controlY1,
                    controlX2, controlY2,
                    endX1, endY1);

            // cubic back to the bottom center
            endX2 = centerX;
            endY2 = dotBottomY;
            controlX1 = endX1;
            controlY1 = endY1 + halfDotRadius;
            controlX2 = centerX + halfDotRadius;
            controlY2 = dotBottomY;
            unselectedDotLeftPath.cubicTo(controlX1, controlY1,
                    controlX2, controlY2,
                    endX2, endY2);

            unselectedDotPath.addPath(unselectedDotLeftPath);

            // now do the next dot to the right
            unselectedDotRightPath.rewind();

            // start at the bottom center
            unselectedDotRightPath.moveTo(nextCenterX, dotBottomY);

            // semi circle to the top center
            rectF.set(nextCenterX - dotRadius, dotTopY, nextCenterX + dotRadius, dotBottomY);
            unselectedDotRightPath.arcTo(rectF, 90, -180, true);

            // cubic to the left middle
            endX1 = nextCenterX - dotRadius - joiningFraction * gap;
            endY1 = dotCenterY;
            controlX1 = nextCenterX - halfDotRadius;
            controlY1 = dotTopY;
            controlX2 = endX1;
            controlY2 = endY1 - halfDotRadius;
            unselectedDotRightPath.cubicTo(controlX1, controlY1,
                    controlX2, controlY2,
                    endX1, endY1);

            // cubic back to the bottom center
            endX2 = nextCenterX;
            endY2 = dotBottomY;
            controlX1 = endX1;
            controlY1 = endY1 + halfDotRadius;
            controlX2 = endX2 - halfDotRadius;
            controlY2 = dotBottomY;
            unselectedDotRightPath.cubicTo(controlX1, controlY1,
                    controlX2, controlY2,
                    endX2, endY2);
            unselectedDotPath.addPath(unselectedDotRightPath);
        }

        if (joiningFraction > 0.5f && joiningFraction < 1f
                && retreatingJoinX1 == INVALID_FRACTION) {

            // case #3 – Joining neighbour, combined curved

            // adjust the fraction so that it goes from 0.3 -> 1 to produce a more realistic 'join'
            float adjustedFraction = (joiningFraction - 0.2f) * 1.25f;

            // start in the bottom left
            unselectedDotPath.moveTo(centerX, dotBottomY);

            // semi-circle to the top left
            rectF.set(centerX - dotRadius, dotTopY, centerX + dotRadius, dotBottomY);
            unselectedDotPath.arcTo(rectF, 90, 180, true);

            // bezier to the middle top of the join
            endX1 = centerX + dotRadius + gap / 2;
            endY1 = dotCenterY - adjustedFraction * dotRadius;
            controlX1 = endX1 - adjustedFraction * dotRadius;
            controlY1 = dotTopY;
            controlX2 = endX1 - (1 - adjustedFraction) * dotRadius;
            controlY2 = endY1;
            unselectedDotPath.cubicTo(controlX1, controlY1,
                    controlX2, controlY2,
                    endX1, endY1);

            // bezier to the top right of the join
            endX2 = nextCenterX;
            endY2 = dotTopY;
            controlX1 = endX1 + (1 - adjustedFraction) * dotRadius;
            controlY1 = endY1;
            controlX2 = endX1 + adjustedFraction * dotRadius;
            controlY2 = dotTopY;
            unselectedDotPath.cubicTo(controlX1, controlY1,
                    controlX2, controlY2,
                    endX2, endY2);

            // semi-circle to the bottom right
            rectF.set(nextCenterX - dotRadius, dotTopY, nextCenterX + dotRadius, dotBottomY);
            unselectedDotPath.arcTo(rectF, 270, 180, true);

            // bezier to the middle bottom of the join
            // endX1 stays the same
            endY1 = dotCenterY + adjustedFraction * dotRadius;
            controlX1 = endX1 + adjustedFraction * dotRadius;
            controlY1 = dotBottomY;
            controlX2 = endX1 + (1 - adjustedFraction) * dotRadius;
            controlY2 = endY1;
            unselectedDotPath.cubicTo(controlX1, controlY1,
                    controlX2, controlY2,
                    endX1, endY1);

            // bezier back to the start point in the bottom left
            endX2 = centerX;
            endY2 = dotBottomY;
            controlX1 = endX1 - (1 - adjustedFraction) * dotRadius;
            controlY1 = endY1;
            controlX2 = endX1 - adjustedFraction * dotRadius;
            controlY2 = endY2;
            unselectedDotPath.cubicTo(controlX1, controlY1,
                    controlX2, controlY2,
                    endX2, endY2);
        }
        if (joiningFraction == 1 && retreatingJoinX1 == INVALID_FRACTION) {

            // case #4 Joining neighbour, combined straight technically we could use case 3 for this
            // situation as well but assume that this is an optimization rather than faffing around
            // with beziers just to draw a rounded rect
            rectF.set(centerX - dotRadius, dotTopY, nextCenterX + dotRadius, dotBottomY);
            unselectedDotPath.addRoundRect(rectF, dotRadius, dotRadius, Path.Direction.CW);
        }

        // case #5 is handled by #getRetreatingJoinPath()
        // this is done separately so that we can have a single retreating path spanning
        // multiple dots and therefore animate it's movement smoothly

        if (dotRevealFraction > MINIMAL_REVEAL) {

            // case #6 – previously hidden dot revealing
            unselectedDotPath.addCircle(centerX, dotCenterY, dotRevealFraction * dotRadius,
                    Path.Direction.CW);
        }

        return unselectedDotPath;
    }

    private Path getRetreatingJoinPath() {
        unselectedDotPath.rewind();
        rectF.set(retreatingJoinX1, dotTopY, retreatingJoinX2, dotBottomY);
        unselectedDotPath.addRoundRect(rectF, dotRadius, dotRadius, Path.Direction.CW);
        return unselectedDotPath;
    }

    private void drawSelected(Canvas canvas) {
        canvas.drawCircle(selectedDotX, dotCenterY, dotRadius, selectedPaint);
    }

    private void setSelectedPage(int now) {
        if (now == currentPage) return;

        pageChanging = true;
        previousPage = currentPage;
        currentPage = now;
        int steps = Math.abs(now - previousPage);

        if (steps > 1) {
            if (now > previousPage) {
                for (int i = 0; i < steps; i++) {
                    setJoiningFraction(previousPage + i, 1f);
                }
            } else {
                for (int i = -1; i > -steps; i--) {
                    setJoiningFraction(previousPage + i, 1f);
                }
            }
        }

        // create the anim to move the selected dot – this animator will kick off
        // retreat animations when it has moved 75% of the way.
        // The retreat animation in turn will kick of reveal anims when the
        // retreat has passed any dots to be revealed
        moveAnimation = createMoveSelectedAnimator(dotCenterX[now], previousPage, now, steps);
        moveAnimation.start();
    }

    private ValueAnimator createMoveSelectedAnimator(
            float moveTo, int was, int now, int steps) {

        // create the actual move animator
        ValueAnimator moveSelected = ValueAnimator.ofFloat(selectedDotX, moveTo);

        // also set up a pending retreat anim – this starts when the move is 75% complete
        retreatAnimation = new InkPageIndicator.PendingRetreatAnimator(was, now, steps,
                now > was ?
                        new InkPageIndicator.RightwardStartPredicate(
                                moveTo - (moveTo - selectedDotX) * 0.25f) :
                        new InkPageIndicator.LeftwardStartPredicate(
                                moveTo + (selectedDotX - moveTo) * 0.25f));
        retreatAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                resetState();
                pageChanging = false;
            }
        });
        moveSelected.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                selectedDotX = (Float) valueAnimator.getAnimatedValue();
                retreatAnimation.startIfNecessary(selectedDotX);
                ViewCompat.postInvalidateOnAnimation(InkPageIndicator.this);
            }
        });
        moveSelected.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // set a flag so that we continue to draw the unselected dot in the target position
                // until the selected dot has finished moving into place
                selectedDotInPosition = false;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // set a flag when anim finishes so that we don't draw both selected & unselected
                // page dots
                selectedDotInPosition = true;
            }
        });
        // slightly delay the start to give the joins a chance to run
        // unless dot isn't in position yet – then don't delay!
        moveSelected.setStartDelay(selectedDotInPosition ? animDuration / 4L : 0L);
        moveSelected.setDuration(animDuration * 3L / 4L);
        moveSelected.setInterpolator(interpolator);
        return moveSelected;
    }

    private void setJoiningFraction(int leftDot, float fraction) {
        if (leftDot < joiningFractions.length) {

            joiningFractions[leftDot] = fraction;
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private void clearJoiningFractions() {
        Arrays.fill(joiningFractions, 0f);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    private void setDotRevealFraction(int dot, float fraction) {
        if (dot < dotRevealFractions.length) {
            dotRevealFractions[dot] = fraction;
        }
        ViewCompat.postInvalidateOnAnimation(this);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        InkPageIndicator.SavedState savedState = (InkPageIndicator.SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        currentPage = savedState.currentPage;
        requestLayout();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        InkPageIndicator.SavedState savedState = new InkPageIndicator.SavedState(superState);
        savedState.currentPage = currentPage;
        return savedState;
    }

    /**
     * A predicate used to start an animation when a test passes
     */
    abstract static class StartPredicate {

        final float thresholdValue;

        StartPredicate(float thresholdValue) {
            this.thresholdValue = thresholdValue;
        }

        abstract boolean shouldStart(float currentValue);

    }

    /**
     * A predicate used to start an animation when a given value is greater than a threshold
     */
    private static class RightwardStartPredicate extends InkPageIndicator.StartPredicate {

        RightwardStartPredicate(float thresholdValue) {
            super(thresholdValue);
        }

        boolean shouldStart(float currentValue) {
            return currentValue > thresholdValue;
        }
    }

    /**
     * A predicate used to start an animation then a given value is less than a threshold
     */
    private static class LeftwardStartPredicate extends InkPageIndicator.StartPredicate {

        LeftwardStartPredicate(float thresholdValue) {
            super(thresholdValue);
        }

        boolean shouldStart(float currentValue) {
            return currentValue < thresholdValue;
        }
    }

    private static class SavedState extends View.BaseSavedState {
        public static final Parcelable.Creator<InkPageIndicator.SavedState> CREATOR =
                new Parcelable.Creator<InkPageIndicator.SavedState>() {
                    @Override
                    public InkPageIndicator.SavedState createFromParcel(Parcel in) {
                        return new InkPageIndicator.SavedState(in);
                    }

                    @Override
                    public InkPageIndicator.SavedState[] newArray(int size) {
                        return new InkPageIndicator.SavedState[size];
                    }
                };
        int currentPage;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentPage = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(currentPage);
        }
    }

    /**
     * A {@link ValueAnimator} that starts once a given predicate returns true.
     */
    @SuppressWarnings({"CloneableClassInSecureContext", "NonStaticInnerClassInSecureContext"})
    abstract class PendingStartAnimator extends ValueAnimator implements Cloneable {

        final InkPageIndicator.StartPredicate predicate;
        boolean hasStarted;

        PendingStartAnimator(InkPageIndicator.StartPredicate predicate) {
            this.predicate = predicate;
            hasStarted = false;
        }

        void startIfNecessary(float currentValue) {
            if (!hasStarted && predicate.shouldStart(currentValue)) {
                start();
                hasStarted = true;
            }
        }
    }

    /**
     * An Animator that shows and then shrinks a retreating join between the previous and newly
     * selected pages.  This also sets up some pending dot reveals – to be started when the retreat
     * has passed the dot to be revealed.
     */
    @SuppressWarnings({"CloneableClassInSecureContext", "NonStaticInnerClassInSecureContext"})
    private class PendingRetreatAnimator extends PendingStartAnimator {

        PendingRetreatAnimator(int was, int now, int steps, StartPredicate predicate) {
            super(predicate);
            setDuration(animHalfDuration);
            setInterpolator(interpolator);

            // work out the start/end values of the retreating join from the direction we're
            // travelling in.  Also look at the current selected dot position, i.e. we're moving on
            // before a prior anim has finished.
            final float initialX1 = now > was ? Math.min(dotCenterX[was], selectedDotX) - dotRadius
                    : dotCenterX[now] - dotRadius;
            float finalX1 = now > was ? dotCenterX[now] - dotRadius
                    : dotCenterX[now] - dotRadius;
            final float initialX2 = now > was ? dotCenterX[now] + dotRadius
                    : Math.max(dotCenterX[was], selectedDotX) + dotRadius;
            float finalX2 = now > was ? dotCenterX[now] + dotRadius
                    : dotCenterX[now] + dotRadius;

            revealAnimations = new InkPageIndicator.PendingRevealAnimator[steps];
            // hold on to the indexes of the dots that will be hidden by the retreat so that
            // we can initialize their revealFraction's i.e. make sure they're hidden while the
            // reveal animation runs
            final int[] dotsToHide = new int[steps];
            if (initialX1 == finalX1) { // (initialX2 != finalX2) leftward retreat
                setFloatValues(initialX2, finalX2);
                // create the reveal animations that will run when the retreat passes them
                for (int i = 0; i < steps; i++) {
                    revealAnimations[i] = new PendingRevealAnimator(was - i,
                            new LeftwardStartPredicate(dotCenterX[was - i]));
                    dotsToHide[i] = was - i;
                }
                addUpdateListener(new AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        retreatingJoinX2 = (Float) valueAnimator.getAnimatedValue();
                        ViewCompat.postInvalidateOnAnimation(InkPageIndicator.this);
                        // start any reveal animations if we've passed them
                        for (PendingRevealAnimator pendingReveal : revealAnimations) {
                            pendingReveal.startIfNecessary(retreatingJoinX2);
                        }
                    }
                });
            } else { // rightward retreat
                setFloatValues(initialX1, finalX1);
                // create the reveal animations that will run when the retreat passes them
                for (int i = 0; i < steps; i++) {
                    revealAnimations[i] = new PendingRevealAnimator(was + i,
                            new RightwardStartPredicate(dotCenterX[was + i]));
                    dotsToHide[i] = was + i;
                }
                addUpdateListener(new AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        retreatingJoinX1 = (Float) valueAnimator.getAnimatedValue();
                        ViewCompat.postInvalidateOnAnimation(InkPageIndicator.this);
                        // start any reveal animations if we've passed them
                        for (PendingRevealAnimator pendingReveal : revealAnimations) {
                            pendingReveal.startIfNecessary(retreatingJoinX1);
                        }
                    }
                });
            }

            addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    clearJoiningFractions();
                    // we need to set this so that the dots are hidden until the reveal anim runs
                    for (int dot : dotsToHide) {
                        setDotRevealFraction(dot, MINIMAL_REVEAL);
                    }
                    retreatingJoinX1 = initialX1;
                    retreatingJoinX2 = initialX2;
                    ViewCompat.postInvalidateOnAnimation(InkPageIndicator.this);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    retreatingJoinX1 = INVALID_FRACTION;
                    retreatingJoinX2 = INVALID_FRACTION;
                    ViewCompat.postInvalidateOnAnimation(InkPageIndicator.this);
                }
            });
        }
    }

    /**
     * An Animator that animates a given dot's revealFraction i.e. scales it up
     */
    @SuppressWarnings({"CloneableClassInSecureContext", "NonStaticInnerClassInSecureContext"})
    private class PendingRevealAnimator extends PendingStartAnimator {

        private final int dot;

        PendingRevealAnimator(int dot, StartPredicate predicate) {
            super(predicate);
            setFloatValues(MINIMAL_REVEAL, 1f);
            this.dot = dot;
            setDuration(animHalfDuration);
            setInterpolator(interpolator);
            addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    setDotRevealFraction(PendingRevealAnimator.this.dot,
                            (Float) valueAnimator.getAnimatedValue());
                }
            });
            addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setDotRevealFraction(PendingRevealAnimator.this.dot, 0f);
                    ViewCompat.postInvalidateOnAnimation(InkPageIndicator.this);
                }
            });
        }
    }
}
