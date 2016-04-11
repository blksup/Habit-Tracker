/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blk.uhabits.widgets;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.*;
import android.widget.Spinner;

import java.lang.ref.WeakReference;

public class SwipeDismissTouchListener implements View.OnTouchListener {

    private static final int LONG_PRESS = 1;
    private static final int LONG_PRESS_SWIPE = 2;
    private static final int CHECK_VELOCITY_TIME = 1000;
    private int mSlop;
    private int mMinFlingVelocity;
    private int mMaxFlingVelocity;
    private long mAnimationTime;

    private View mView;
    private View mTargetView;
    private DismissCallbacks mCallbacks;
    private SwipeCallbacks mSwipeCallbacks;
    private UndoCallbacks mUndoCallbacks;
    private int mViewSize = 1; // 1 and not 0 to prevent dividing by zero
    private int mDirection = 0;
    private boolean mPortrait;

    private float mDownX;
    private float mDownY;
    private float mWaitX;
    private boolean mSwiping;
    private VelocityTracker mVelocityTracker;
    private float mTranslation;
    private boolean mIsInLongPress;
    private boolean mIsSupportLongPress = false;
    private boolean mIsSupportLongPressSwipe = false;
    private boolean mAlwaysInTapRegion;
    private boolean mIsTarget = false;
    private boolean mIsSupportClick = true;
    private int mOptionMenuMode;
    private boolean mIsInvincible = false;
    private boolean mIsOpened = false;
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
    private static SwipeDismissTouchListener waitingListener;
    private PressHandler mHandler;

    static class PressHandler extends Handler {
        private final WeakReference<SwipeDismissTouchListener> mActivity;

        PressHandler(SwipeDismissTouchListener activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            SwipeDismissTouchListener activity = mActivity.get();
            if (activity != null) {
                activity.handleMessage(msg);
            }
        }
    }

    public interface DismissCallbacks {
        boolean canDismiss();

        void onDismiss();

        void onClick();

        void onLongPress();

        void onCancel();
    }

    public interface SwipeCallbacks {
        int onSwipeFrom(boolean right);

        void onSetAlpha(float alpha);

        void onSwipeFinish();
    }

    public interface UndoCallbacks {
        boolean isUndoView();
    }

    private static class VelocityValue {
        float mVelocityX;
        float mVelocityY;
        float mAbsVelocityX;
        float mAbsVelocityY;

        public VelocityValue(VelocityTracker velocityTracker) {
            velocityTracker.computeCurrentVelocity(CHECK_VELOCITY_TIME);
            mVelocityX = velocityTracker.getXVelocity();
            mVelocityY = velocityTracker.getYVelocity();
            mAbsVelocityX = Math.abs(mVelocityX);
            mAbsVelocityY = Math.abs(mVelocityY);
        }
    }

    public SwipeDismissTouchListener(View view, View targetView, DismissCallbacks callbacks) {
        this(view, targetView, callbacks, true);
    }

    public SwipeDismissTouchListener(View view, View targetView, DismissCallbacks callbacks, boolean portrait) {
        ViewConfiguration vc = ViewConfiguration.get(view.getContext());
        mSlop = vc.getScaledTouchSlop();
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity() * 16;
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mAnimationTime = view.getContext().getResources().getInteger(
                android.R.integer.config_shortAnimTime);
        mView = view;
        mTargetView = targetView;
        mCallbacks = callbacks;
        mPortrait = portrait;
        mHandler = new PressHandler(this);
    }

    public static SwipeDismissTouchListener getWaitingListener() {
        return waitingListener;
    }

    private void handleMessage(Message msg) {
        switch (msg.what) {
            case LONG_PRESS:
                mCallbacks.onLongPress();
                mIsInLongPress = true;
                break;
            case LONG_PRESS_SWIPE:
                mSwiping = true;
                mView.getParent().requestDisallowInterceptTouchEvent(true);
                break;
            default:
                break;
        }
    }

    private int getOpenWidth(int count, int width) {
        if (count == 1) {
            return width / 4;
        } else if (count == 2) {
            return width / 2;
        } else if (count == 3) {
            return (width / 4) * 3;
        }
        return width;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        motionEvent.offsetLocation(mTranslation, 0);

        Log.d("ANTT", "onTouch");

        if (mViewSize < 2) {
            mViewSize = mPortrait ? mView.getWidth() : mView.getHeight();
        }

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (waitingListener != null && waitingListener.mView != mView) {
                    waitingListener.cancelSwipe();
                }
                mDownX = motionEvent.getRawX();
                mDownY = motionEvent.getRawY();
                mWaitX = mView.getTranslationX();
                if (mWaitX == 0) {
                    mDirection = 0;
                }
                int[] position = new int[2];
                mTargetView.getLocationOnScreen(position);
                if (mDownY >= position[1] && mDownY <= position[1] + mTargetView.getHeight()) {
                    mIsTarget = true;
                } else {
                    mIsTarget = false;
                    return false;
                }
                mIsInLongPress = false;
                mAlwaysInTapRegion = true;
                if (mCallbacks.canDismiss()) {
                    mVelocityTracker = VelocityTracker.obtain();
                    mVelocityTracker.addMovement(motionEvent);
                }
                if (mIsSupportLongPress) {
                    mHandler.removeMessages(LONG_PRESS);
                    mHandler.sendEmptyMessageAtTime(LONG_PRESS, motionEvent.getDownTime()
                            + TAP_TIMEOUT + LONGPRESS_TIMEOUT);
                }
                if (mIsSupportLongPressSwipe) {
                    mHandler.removeMessages(LONG_PRESS_SWIPE);
                    mHandler.sendEmptyMessageAtTime(LONG_PRESS_SWIPE, motionEvent.getDownTime()
                            + TAP_TIMEOUT + LONGPRESS_TIMEOUT);
                }

                return mIsSupportClick && mIsTarget;
            }

            case MotionEvent.ACTION_UP: {
                if (mUndoCallbacks != null && mUndoCallbacks.isUndoView()) {
                    break;
                }
                if (mVelocityTracker == null || !mIsTarget) {
                    break;
                }
                if (mAlwaysInTapRegion) {
                    mHandler.removeMessages(LONG_PRESS);
                    mHandler.removeMessages(LONG_PRESS_SWIPE);
                    if (mIsSupportClick) {
                        mCallbacks.onClick();
                    }
                }
                mVelocityTracker.addMovement(motionEvent);
                VelocityValue velocity = new VelocityValue(mVelocityTracker);
                if (mPortrait) {
                    float deltaX = motionEvent.getRawX() - mDownX;
                    float waitX = mView.getTranslationX();
                    boolean dismiss = false;
                    boolean waitDismiss = false;
                    boolean dismissRight = false;
                    if (mSwipeCallbacks != null && mOptionMenuMode > 0) {
                        float bound = mOptionMenuMode == 1 ? 8 : 4;
                        if ((Math.abs(waitX) > mViewSize / bound)
                                && mSwiping) {
                            waitDismiss = true;
                            dismissRight = waitX > 0;
                        }
                    } else {
                        if (mWaitX < 0 || deltaX < 0 || mIsInvincible) {
                            dismiss = false;
                        } else if (Math.abs(deltaX) > (float)mViewSize / 2f && mSwiping) {
                            dismiss = true;
                            dismissRight = deltaX > 0;
                        } else if (mMinFlingVelocity <= velocity.mAbsVelocityX && velocity.mAbsVelocityX <= mMaxFlingVelocity
                                && velocity.mAbsVelocityY < velocity.mAbsVelocityX && mSwiping
                                && Math.abs(deltaX) >  (float)mViewSize / 4f) {
                            dismiss = (velocity.mVelocityX < 0) == (deltaX < 0);
                            dismissRight = velocity.mVelocityX > 0;
                        }
                    }
                    mIsOpened = false;
                    if (waitDismiss) {
                        waitingListener = SwipeDismissTouchListener.this;
                        mIsOpened = true;
                        mView.animate()
                                .translationX(dismissRight ? (float)mViewSize / 2f : -getOpenWidth(mOptionMenuMode, mViewSize))
                                .alpha(1f)
                                .setDuration(mAnimationTime)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        mView.setPressed(false);
                                        if (mSwipeCallbacks != null) {
                                            mSwipeCallbacks.onSetAlpha(1f);
                                        }
                                    }
                                });
                    } else if (dismiss) {
                        mView.animate()
                                .translationX(dismissRight ? mViewSize : -mViewSize)
                                .alpha(0f)
                                .setDuration(mAnimationTime)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        mCallbacks.onDismiss();
                                    }
                                });
                    } else if (mSwiping) {
                        cancelSwipe();
                        if (mIsInLongPress) mCallbacks.onCancel();
                    } else if (mIsInLongPress) {
                        mCallbacks.onCancel();
                    }
                    //enableSpinner((ViewGroup)mView, true);
                } else {
                    float deltaY = motionEvent.getRawY() - mDownY;
                    boolean dismiss = false;
                    boolean dismissDown = false;
                    if (Math.abs(deltaY) > (float)mViewSize / 2f && mSwiping) {
                        dismiss = true;
                        dismissDown = deltaY > 0;
                    } else if (mMinFlingVelocity <= velocity.mAbsVelocityX && velocity.mAbsVelocityX <= mMaxFlingVelocity
                            && velocity.mAbsVelocityY < velocity.mAbsVelocityX && mSwiping) {
                        dismiss = (velocity.mVelocityY < 0) == (deltaY < 0);
                        dismissDown = velocity.mVelocityY > 0;
                    }
                    if (dismiss) {
                        // dismiss
                        mView.animate()
                                .translationY(dismissDown ? mViewSize : -mViewSize)
                                .alpha(0f)
                                .setDuration(mAnimationTime)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        mCallbacks.onDismiss();
                                    }
                                });
                    } else if (mSwiping) {
                        // cancel
                        mView.animate()
                                .translationY(0)
                                .alpha(1)
                                .setDuration(mAnimationTime)
                                .setListener(null);
                        if (mIsInLongPress) mCallbacks.onCancel();
                    } else if (mIsInLongPress) {
                        mCallbacks.onCancel();
                    }
                }
                boolean canceled = false;
                if (!mIsSupportClick && mIsTarget && mSwiping) {
                    mView.setPressed(false);
                    canceled = true;
                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;
                mTranslation = 0;
                mDownX = 0;
                mDownY = 0;
                mSwiping = false;
                mIsInLongPress = false;
                return canceled;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mVelocityTracker == null || !mIsTarget) {
                    break;
                }
                cancelSwipe();
                mVelocityTracker.recycle();
                mVelocityTracker = null;
                mTranslation = 0;
                mDownX = 0;
                mDownY = 0;
                mSwiping = false;
                if (mIsInLongPress) mCallbacks.onCancel();
                mHandler.removeMessages(LONG_PRESS);
                mHandler.removeMessages(LONG_PRESS_SWIPE);
                mIsInLongPress = false;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mUndoCallbacks != null && mUndoCallbacks.isUndoView()) {
                    break;
                }
                if (mVelocityTracker == null || !mIsTarget) {
                    break;
                }
                if (mAlwaysInTapRegion) {
                    final int deltaX = (int) (motionEvent.getRawX() - mDownX);
                    final int deltaY = (int) (motionEvent.getRawY() - mDownY);
                    int distance = (deltaX * deltaX) + (deltaY * deltaY);
                    if (distance > mSlop * mSlop) {
                        mAlwaysInTapRegion = false;
                        mHandler.removeMessages(LONG_PRESS);
                        mHandler.removeMessages(LONG_PRESS_SWIPE);
                    }
                }

                if (!mIsSupportLongPress || mIsInLongPress) {
                    mVelocityTracker.addMovement(motionEvent);
                    float deltaX = motionEvent.getRawX() - mDownX;
                    float deltaY = motionEvent.getRawY() - mDownY;
                    if (mPortrait) {
                        if (Math.abs(deltaX) > mSlop && Math.abs(deltaY) < Math.abs(deltaX) / 2f) {
                            if (!mIsSupportClick) {
                                mView.setPressed(false);
                            }

                            if (!mIsSupportLongPressSwipe || mWaitX != 0) {
                                if (!mSwiping) {
                                    mDownX = motionEvent.getRawX();
                                    deltaX = 0;
                                }
                                mSwiping = true;
                                mView.getParent().requestDisallowInterceptTouchEvent(true);
                                //enableSpinner((ViewGroup)mView, false);
                            }

                            sendCancelEvent(mView, motionEvent);
                        }

                        if (mSwiping) {
                            mTranslation = deltaX;
                            if (mSwipeCallbacks == null && mIsSupportLongPress) {
                                return true;
                            }
                            if (mIsInvincible) {
                                mView.setTranslationX((mWaitX + deltaX) / 4);
                            } else if (mOptionMenuMode > 0) {
                                if (mWaitX + deltaX < -getOpenWidth(mOptionMenuMode, mViewSize)) {
                                    mView.setTranslationX(-getOpenWidth(mOptionMenuMode, mViewSize));
                                } else if (mWaitX + deltaX > 0) {
                                    mView.setTranslationX(0);
                                } else {
                                    mView.setTranslationX(mWaitX + deltaX);
                                }
                            } else if (mWaitX + deltaX < 0) {
                                mView.setTranslationX((mWaitX + deltaX) / 4);
                            } else {
                                mView.setTranslationX(mWaitX + deltaX);
                            }
                            if (mSwipeCallbacks != null) {
                                if (mDirection == 0) {
                                    mDirection = mWaitX + deltaX > 0 ? 1 : -1;
                                    mOptionMenuMode = mSwipeCallbacks.onSwipeFrom(mWaitX + deltaX > 0);
                                } else if (mDirection != (mWaitX + deltaX > 0 ? 1 : -1)) {
                                    mDirection = 0;
                                }
                            }
                            float alpha = 1f - 1f * Math.abs(mView.getTranslationX()) / (float)mViewSize;
                            if (mView.getHeight() < 3000) {
                                mView.setAlpha(mOptionMenuMode > 0 ? 1f : alpha);
                                if (mSwipeCallbacks != null) {
                                    mSwipeCallbacks.onSetAlpha(mOptionMenuMode > 0 ? (1f - alpha) * (4f / (float)mOptionMenuMode) : alpha);
                                }
                            }
                            return true;
                        }
                    } else {
                        if (Math.abs(deltaY) > mSlop && Math.abs(deltaX) < Math.abs(deltaY) / 2) {
                            if (!mIsSupportClick) {
                                mView.setPressed(false);
                            }
                            if (!mIsSupportLongPressSwipe) {
                                mSwiping = true;
                                mView.getParent().requestDisallowInterceptTouchEvent(true);
                            }

                            sendCancelEvent(mView, motionEvent);
                        }

                        if (mSwiping) {
                            mTranslation = deltaY;
                            mView.setTranslationY(deltaY);
                            mView.setAlpha(1f - 1f * Math.abs(deltaY) / mViewSize);
                            return true;
                        }
                    }
                }
                break;
            }
            default:
                break;
        }
        return false;
    }

    private void enableSpinner(ViewGroup viewGroup, boolean bEnable) {
        int childCount = viewGroup.getChildCount();
        for (int i = 0 ; i < childCount ; i++) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof Spinner) {
                view.setEnabled(bEnable);
            } else if (view instanceof ViewGroup) {
                enableSpinner((ViewGroup)view, bEnable);
            }
        }
    }

    private static void sendCancelEvent(View view, MotionEvent motionEvent) {
        MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
        cancelEvent.setAction(MotionEvent.ACTION_CANCEL | (motionEvent.getActionIndex()
                << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        view.onTouchEvent(cancelEvent);
        cancelEvent.recycle();
    }

    public void cancelSwipe() {
        mView.animate()
                .translationX(0)
                .alpha(1)
                .setDuration(mAnimationTime)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mView.setPressed(false);
                        if (mSwipeCallbacks != null) {
                            mSwipeCallbacks.onSwipeFinish();
                        }
                        mDirection = 0;
                        mIsOpened = false;
                    }
                });
    }

    public void setSupportLongPress(boolean set) {
        mIsSupportLongPress = set;
    }

    public void setSupportClick(boolean set) {
        mIsSupportClick = set;
    }

    public void setInvincible(boolean invincible) {
        mIsInvincible = invincible;
    }

    public void setSupportLongPressSwipe(boolean set) {
        mIsSupportLongPressSwipe = set;
    }

    public void setSwipeCallbacks(SwipeCallbacks swipeCallbacks) {
        mSwipeCallbacks = swipeCallbacks;
    }

    public void setUndoCallbacks(UndoCallbacks undoCallbacks) {
        mUndoCallbacks = undoCallbacks;
    }

    public boolean isOpened() {
        return mIsOpened;
    }
}
