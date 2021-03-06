/*
 * Copyright (C) 2010 The Android Open Source Project
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
 */

package com.android.internal.view.menu;

import android.annotation.MiuiHook;
import android.annotation.MiuiHook.MiuiHookType;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @hide
 */
public class ActionMenuItemView extends TextView
        implements MenuView.ItemView, View.OnClickListener, View.OnLongClickListener,
        ActionMenuView.ActionMenuChildView {
    @MiuiHook(MiuiHookType.NEW_FIELD)
    int mSavedPaddingRight;

    private static final String TAG = "ActionMenuItemView";

    private MenuItemImpl mItemData;
    private CharSequence mTitle;
    private Drawable mIcon;
    private MenuBuilder.ItemInvoker mItemInvoker;

    private boolean mAllowTextWithIcon;
    private boolean mExpandedFormat;
    private int mMinWidth;
    private int mSavedPaddingLeft;

    public ActionMenuItemView(Context context) {
        this(context, null);
    }

    public ActionMenuItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionMenuItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources res = context.getResources();
        mAllowTextWithIcon = res.getBoolean(
                com.android.internal.R.bool.config_allowActionMenuItemTextWithIcon);
        TypedArray a = context.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.ActionMenuItemView, 0, 0);
        mMinWidth = a.getDimensionPixelSize(
                com.android.internal.R.styleable.ActionMenuItemView_minWidth, 0);
        a.recycle();

        setOnClickListener(this);
        setOnLongClickListener(this);

        mSavedPaddingLeft = -1;
    }

    @Override
    @MiuiHook(MiuiHookType.CHANGE_CODE)
    public void setPadding(int l, int t, int r, int b) {
        mSavedPaddingLeft = l;
        mSavedPaddingRight = r; //miui add
        super.setPadding(l, t, r, b);
    }

    public MenuItemImpl getItemData() {
        return mItemData;
    }

    public void initialize(MenuItemImpl itemData, int menuType) {
        mItemData = itemData;

        setIcon(itemData.getIcon());
        setTitle(itemData.getTitleForItemView(this)); // Title only takes effect if there is no icon
        setId(itemData.getItemId());

        setVisibility(itemData.isVisible() ? View.VISIBLE : View.GONE);
        setEnabled(itemData.isEnabled());
    }

    public void onClick(View v) {
        if (mItemInvoker != null) {
            mItemInvoker.invokeItem(mItemData);
        }
    }

    public void setItemInvoker(MenuBuilder.ItemInvoker invoker) {
        mItemInvoker = invoker;
    }

    public boolean prefersCondensedTitle() {
        return true;
    }

    public void setCheckable(boolean checkable) {
        // TODO Support checkable action items
    }

    public void setChecked(boolean checked) {
        // TODO Support checkable action items
    }

    public void setExpandedFormat(boolean expandedFormat) {
        if (mExpandedFormat != expandedFormat) {
            mExpandedFormat = expandedFormat;
            if (mItemData != null) {
                mItemData.actionFormatChanged();
            }
        }
    }

    @MiuiHook(MiuiHookType.CHANGE_CODE)
    private void updateTextButtonVisibility() {
        boolean visible = !TextUtils.isEmpty(mTitle);
        visible &= mIcon == null || mItemData.isForceShowText() || // miui modify
                (mItemData.showsTextAsAction() && (mAllowTextWithIcon || mExpandedFormat));

        setText(visible ? mTitle : null);
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
        setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);

        updateTextButtonVisibility();
    }

    public boolean hasText() {
        return !TextUtils.isEmpty(getText());
    }

    public void setShortcut(boolean showShortcut, char shortcutKey) {
        // Action buttons don't show text for shortcut keys.
    }

    public void setTitle(CharSequence title) {
        mTitle = title;

        setContentDescription(mTitle);
        updateTextButtonVisibility();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return true;
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        final CharSequence cdesc = getContentDescription();
        if (!TextUtils.isEmpty(cdesc)) {
            event.getText().add(cdesc);
        }
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Don't allow children to hover; we want this to be treated as a single component.
        return onHoverEvent(event);
    }

    public boolean showsIcon() {
        return true;
    }

    public boolean needsDividerBefore() {
        return hasText() && mItemData.getIcon() == null;
    }

    public boolean needsDividerAfter() {
        return hasText();
    }

    @Override
    public boolean onLongClick(View v) {
        if (hasText()) {
            // Don't show the cheat sheet for items that already show text.
            return false;
        }

        final int[] screenPos = new int[2];
        final Rect displayFrame = new Rect();
        getLocationOnScreen(screenPos);
        getWindowVisibleDisplayFrame(displayFrame);

        final Context context = getContext();
        final int width = getWidth();
        final int height = getHeight();
        final int midy = screenPos[1] + height / 2;
        final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;

        Toast cheatSheet = Toast.makeText(context, mItemData.getTitle(), Toast.LENGTH_SHORT);
        if (midy < displayFrame.height()) {
            // Show along the top; follow action buttons
            cheatSheet.setGravity(Gravity.TOP | Gravity.RIGHT,
                    screenWidth - screenPos[0] - width / 2, height);
        } else {
            // Show along the bottom center
            cheatSheet.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, height);
        }
        cheatSheet.show();
        return true;
    }

    @Override
    @MiuiHook(MiuiHookType.CHANGE_CODE)
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (miuiOnMeasure(widthMeasureSpec, heightMeasureSpec)) {
            return;
        } // miui add

        final boolean textVisible = hasText();
        if (textVisible && mSavedPaddingLeft >= 0) {
            super.setPadding(mSavedPaddingLeft, getPaddingTop(),
                    getPaddingRight(), getPaddingBottom());
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int oldMeasuredWidth = getMeasuredWidth();
        final int targetWidth = widthMode == MeasureSpec.AT_MOST ? Math.min(widthSize, mMinWidth)
                : mMinWidth;

        if (widthMode != MeasureSpec.EXACTLY && mMinWidth > 0 && oldMeasuredWidth < targetWidth) {
            // Remeasure at exactly the minimum width.
            super.onMeasure(MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY),
                    heightMeasureSpec);
        }

        if (!textVisible && mIcon != null) {
            // TextView won't center compound drawables in both dimensions without
            // a little coercion. Pad in to center the icon after we've measured.
            final int w = getMeasuredWidth();
            final int dw = mIcon.getIntrinsicWidth();
            super.setPadding((w - dw) / 2, getPaddingTop(), getPaddingRight(), getPaddingBottom());
        }
    }

    @MiuiHook(MiuiHookType.NEW_METHOD)
    boolean miuiOnMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mSavedPaddingLeft >= 0) {
            super.setPadding(mSavedPaddingLeft, getPaddingTop(),
                    mSavedPaddingRight, getPaddingBottom());
        }

        super.onMeasure(MeasureSpec.UNSPECIFIED, heightMeasureSpec);

        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            final int contentWidth = getMeasuredWidth();
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);


            if (!hasText() && mIcon != null) {
                // TextView won't center compound drawables in both dimensions without
                // a little coercion. Pad in to center the icon after we've measured.
                final int w = getMeasuredWidth();
                final int dw = mIcon.getIntrinsicWidth();
                super.setPadding((w - dw) / 2, getPaddingTop(), getPaddingRight(), getPaddingBottom());
            } else {
                final int w = getMeasuredWidth();
                final int extraWidth = (w - contentWidth) / 2;
                super.setPadding(
                        mPaddingLeft + extraWidth, mPaddingTop,
                        mPaddingRight + extraWidth, mPaddingBottom);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
        return true;
    }
}
