/*
 * Copyright (C) 2016 Evgenii Zagumennyi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.zagum.switchicon;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Region;
import android.os.Build;
import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class SwitchIconView extends AppCompatImageView {

  public static final int ENABLED = 0;
  public static final int DISABLED = 1;

  private static final int DEFAULT_ANIMATION_DURATION = 300;
  private static final float DASH_THICKNESS_PART = 1f / 12f;
  private static final float DEFAULT_DISABLED_ALPHA = .5f;
  private static final float SIN_45 = (float) Math.sin(Math.toRadians(45));

  @IntDef({
      ENABLED,
      DISABLED
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface State {
  }

  private final long animationDuration;
  @FloatRange(from = 0f, to = 1f)
  private final float disabledStateAlpha;
  private final int dashXStart;
  private final int dashYStart;
  private final Path clipPath;
  private int dashThickness;
  private int dashLengthXProjection;
  private int dashLengthYProjection;

  @State
  private int currentState = ENABLED;
  @FloatRange(from = 0f, to = 1f)
  private float fraction = 0f;

  @NonNull
  private final Paint dashPaint;
  @NonNull
  private final Point dashStart = new Point();
  @NonNull
  private final Point dashEnd = new Point();

  public SwitchIconView(@NonNull Context context) {
    this(context, null);
  }

  public SwitchIconView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SwitchIconView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    TypedArray array = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SwitchIconView, 0, 0);

    int iconTintColor;
    try {
      iconTintColor = array.getColor(R.styleable.SwitchIconView_si_tint_color, Color.BLACK);
      animationDuration = array.getInteger(R.styleable.SwitchIconView_si_animation_duration, DEFAULT_ANIMATION_DURATION);
      disabledStateAlpha = array.getFloat(R.styleable.SwitchIconView_si_disabled_alpha, DEFAULT_DISABLED_ALPHA);
    } finally {
      array.recycle();
    }

    if (disabledStateAlpha < 0f || disabledStateAlpha > 1f) {
      throw new IllegalArgumentException("Wrong value for si_disabled_alpha [" + disabledStateAlpha + "]. "
          + "Must be value from range [0, 1]");
    }

    setColorFilter(new PorterDuffColorFilter(iconTintColor, PorterDuff.Mode.SRC_IN));

    dashXStart = getPaddingLeft();
    dashYStart = getPaddingTop();

    dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dashPaint.setStyle(Paint.Style.STROKE);
    dashPaint.setColorFilter(new PorterDuffColorFilter(iconTintColor, PorterDuff.Mode.SRC_IN));

    clipPath = new Path();

    initDashCoordinates();
  }

  /**
   * Changes state with animation
   *
   * @param state {@link #ENABLED} or {@link #DISABLED}
   * @throws IllegalArgumentException if {@param state} is invalid
   */
  public void setState(@State int state) {
    setState(state, true);
  }

  /**
   * Changes state
   *
   * @param state {@link #ENABLED} or {@link #DISABLED}
   * @param animate Indicates that state will be changed with or without animation
   * @throws IllegalArgumentException if {@param state} is invalid
   */
  public void setState(@State int state, boolean animate) {
    if (state == currentState) return;
    if (state != ENABLED && state != DISABLED) {
      throw new IllegalArgumentException("Unknown state [" + state + "]");
    }
    switchState(animate);
  }

  /**
   * Switches state between values {@link #ENABLED} or {@link #DISABLED}
   * with animation
   */
  public void switchState() {
    switchState(true);
  }

  /**
   * Switches state between values {@link #ENABLED} or {@link #DISABLED}
   * with animation
   *
   * @param animate Indicates that state will be changed with or without animation
   */
  public void switchState(boolean animate) {
    float newFraction;
    if (currentState == ENABLED) {
      newFraction = 1f;
      currentState = DISABLED;
    } else {
      newFraction = 0f;
      currentState = ENABLED;
    }
    if (animate) {
      animateToFraction(newFraction);
    } else {
      setFraction(newFraction);
      invalidate();
    }
  }

  private void animateToFraction(float toFraction) {
    ValueAnimator animator = ValueAnimator.ofFloat(fraction, toFraction);
    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        setFraction((float) animation.getAnimatedValue());
      }
    });
    animator.setInterpolator(new DecelerateInterpolator());
    animator.setDuration(animationDuration);
    animator.start();
  }

  private void setFraction(float fraction) {
    this.fraction = fraction;
    int alpha = (int) ((disabledStateAlpha + (1f - fraction) * (1f - disabledStateAlpha)) * 255);
    updateImageAlphaWithoutInvalidate(alpha);
    dashPaint.setAlpha(alpha);
    updateClipPath();
    postInvalidateOnAnimationCompat();
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    dashLengthXProjection = width - getPaddingLeft() - getPaddingRight();
    dashLengthYProjection = height - getPaddingTop() - getPaddingBottom();
    dashThickness = (int) (DASH_THICKNESS_PART * (dashLengthXProjection + dashLengthYProjection) / 2f);
    dashPaint.setStrokeWidth(dashThickness);
    initDashCoordinates();
    updateClipPath();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    drawDash(canvas);
    canvas.clipPath(clipPath, Region.Op.XOR);
    super.onDraw(canvas);
  }

  private void initDashCoordinates() {
    float delta1 = 1.5f * SIN_45 * dashThickness;
    float delta2 = 0.5f * SIN_45 * dashThickness;
    dashStart.x = (int) (dashXStart + delta2);
    dashStart.y = dashYStart + (int) (delta1);
    dashEnd.x = (int) (dashXStart + dashLengthXProjection - delta1);
    dashEnd.y = (int) (dashYStart + dashLengthYProjection - delta2);
  }

  private void updateClipPath() {
    float delta = dashThickness / SIN_45;
    clipPath.reset();
    clipPath.moveTo(dashXStart, dashYStart + delta);
    clipPath.lineTo(dashXStart + delta, dashYStart);
    clipPath.lineTo(dashXStart + dashLengthXProjection * fraction, dashYStart + dashLengthYProjection * fraction - delta);
    clipPath.lineTo(dashXStart + dashLengthXProjection * fraction - delta, dashYStart + dashLengthYProjection * fraction);
  }

  private void drawDash(Canvas canvas) {
    float x = fraction * (dashEnd.x - dashStart.x) + dashStart.x;
    float y = fraction * (dashEnd.y - dashStart.y) + dashStart.y;
    canvas.drawLine(dashStart.x, dashStart.y, x, y, dashPaint);
  }

  private void postInvalidateOnAnimationCompat() {
    final long fakeFrameTime = 10;
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
      postInvalidateOnAnimation();
    } else {
      postInvalidateDelayed(fakeFrameTime);
    }
  }

  private void updateImageAlphaWithoutInvalidate(int alpha) {
    alpha &= 0xFF;
    ReflectionUtils.setValue(this, "mAlpha", alpha);
    ReflectionUtils.setValue(this, "mColorMod", true);
    Class<?> noParams[] = {};
    ReflectionUtils.callMethod(this, "applyColorMod", noParams);
  }
}
