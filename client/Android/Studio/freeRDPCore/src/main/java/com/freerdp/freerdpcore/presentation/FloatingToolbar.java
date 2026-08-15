/*
   Floating toolbar for RDP session controls

   Copyright 2026 Ibrahim Sevinc <ibrahim.sevinc.mail@gmail.com>

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.freerdp.freerdpcore.R;

public class FloatingToolbar
{
	public interface Listener
	{
		void onToggleTouchPointer();
		void onToggleSysKeyboard();
		void onToggleExtKeyboard();
		void onToggleMagnifier();
	}

	private enum Edge
	{
		LEFT,
		RIGHT,
		TOP,
		BOTTOM
	}

	private final LinearLayout container;
	private final LinearLayout buttons;
	private boolean expanded = false;
	private int insetLeft = 0, insetTop = 0, insetRight = 0, insetBottom = 0;
	private Edge snappedEdge = Edge.LEFT;
	private float snappedFraction = 0.4f;

	// Toggle buttons whose tint reflects current on/off state -- see refreshToggleStates().
	private final ImageButton touchPointerButton, magnifierButton, sysKeyboardButton,
	    extKeyboardButton;
	private final ColorStateList activeTint, inactiveTint;

	public void setInsets(int left, int top, int right, int bottom)
	{
		insetLeft = left;
		insetTop = top;
		insetRight = right;
		insetBottom = bottom;
	}

	public FloatingToolbar(Activity activity, Listener listener)
	{
		container = activity.findViewById(R.id.floating_toolbar_container);
		buttons = activity.findViewById(R.id.floating_toolbar_buttons);
		View handle = activity.findViewById(R.id.floating_toolbar_handle);
		setTooltip(handle);

		// Background only, not the buttons/icons -- mutate() so we don't stomp on other views
		// sharing the same drawable resource.
		float opacity = ApplicationSettingsActivity.getToolbarOpacity(activity);
		container.getBackground().mutate().setAlpha(Math.round(opacity * 255f));

		GestureDetector gestureDetector =
		    new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
			    @Override public boolean onSingleTapConfirmed(MotionEvent e)
			    {
				    toggle();
				    return true;
			    }
		    });

		View.OnTouchListener dragListener = buildDragListener(activity, gestureDetector, handle);
		container.setOnTouchListener(dragListener);
		handle.setOnTouchListener(dragListener);
		for (int i = 0; i < buttons.getChildCount(); i++)
			buttons.getChildAt(i).setOnTouchListener(dragListener);

		touchPointerButton = bindButton(activity, R.id.floating_toolbar_touch_pointer,
		                                listener::onToggleTouchPointer);
		magnifierButton =
		    bindButton(activity, R.id.floating_toolbar_magnifier, listener::onToggleMagnifier);
		sysKeyboardButton =
		    bindButton(activity, R.id.floating_toolbar_sys_keyboard, listener::onToggleSysKeyboard);
		extKeyboardButton =
		    bindButton(activity, R.id.floating_toolbar_ext_keyboard, listener::onToggleExtKeyboard);

		activeTint = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.tp_accent));
		inactiveTint = ColorStateList.valueOf(Color.WHITE);

		ViewTreeObserver vto = container.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override public void onGlobalLayout()
			{
				container.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				View parent = (View)container.getParent();
				if (parent == null)
					return;
				positionInitial(parent);
				parent.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop,
				                                  oldRight, oldBottom) -> {
					if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
						resnapToSameEdge();
				});
			}
		});
	}

	private void positionInitial(View parent)
	{
		container.setOrientation(LinearLayout.VERTICAL);
		buttons.setOrientation(LinearLayout.VERTICAL);
		snappedEdge = Edge.LEFT;
		snappedFraction = 0.4f;
		float ph = parent.getHeight();
		float ch = container.getHeight();
		container.setX(insetLeft);
		container.setY(
		    Math.max(insetTop, insetTop + snappedFraction * (ph - insetTop - insetBottom - ch)));
	}

	private void resnapToSameEdge()
	{
		View parent = (View)container.getParent();
		if (parent == null)
			return;

		int orientation = (snappedEdge == Edge.LEFT || snappedEdge == Edge.RIGHT)
		                      ? LinearLayout.VERTICAL
		                      : LinearLayout.HORIZONTAL;
		container.setOrientation(orientation);
		buttons.setOrientation(orientation);

		container.post(() -> {
			float pw = parent.getWidth(), ph = parent.getHeight();
			float w = container.getWidth(), h = container.getHeight();
			float tx, ty;
			switch (snappedEdge)
			{
				case LEFT:
					tx = insetLeft;
					ty = insetTop + snappedFraction * (ph - insetTop - insetBottom - h);
					break;
				case RIGHT:
					tx = pw - w - insetRight;
					ty = insetTop + snappedFraction * (ph - insetTop - insetBottom - h);
					break;
				case TOP:
					tx = insetLeft + snappedFraction * (pw - insetLeft - insetRight - w);
					ty = insetTop;
					break;
				default:
					tx = insetLeft + snappedFraction * (pw - insetLeft - insetRight - w);
					ty = ph - h - insetBottom;
					break;
			}
			tx = Math.max(insetLeft, Math.min(tx, pw - w - insetRight));
			ty = Math.max(insetTop, Math.min(ty, ph - h - insetBottom));
			container.animate().x(tx).y(ty).setDuration(250).start();
		});
	}

	private void toggle()
	{
		expanded = !expanded;
		buttons.setVisibility(expanded ? View.VISIBLE : View.GONE);
		snap();
	}

	private ImageButton bindButton(Activity activity, int id, Runnable action)
	{
		ImageButton v = activity.findViewById(id);
		if (v != null)
		{
			setTooltip(v);
			v.setOnClickListener(ignored -> action.run());
		}
		return v;
	}

	// Tints each toggle button to reflect whether the feature it controls is currently on,
	// so on/off state is visible at a glance instead of only right after tapping. Call after
	// any action that might have changed one of these (see SessionActivity's Listener impl).
	public void refreshToggleStates(boolean touchPointer, boolean magnifier, boolean sysKeyboard,
	                                boolean extKeyboard)
	{
		setActive(touchPointerButton, touchPointer);
		setActive(magnifierButton, magnifier);
		setActive(sysKeyboardButton, sysKeyboard);
		setActive(extKeyboardButton, extKeyboard);
	}

	private void setActive(ImageButton button, boolean active)
	{
		if (button != null)
			ImageViewCompat.setImageTintList(button, active ? activeTint : inactiveTint);
	}

	private static void setTooltip(View v)
	{
		v.setTooltipText(v.getContentDescription());
	}

	private View.OnTouchListener buildDragListener(Activity activity,
	                                               GestureDetector gestureDetector, View handle)
	{
		int touchSlop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
		return new View.OnTouchListener() {
			private float startX, startY, offsetX, offsetY;
			private boolean dragging;

			@Override public boolean onTouch(View v, MotionEvent e)
			{
				if (v == handle || v == container)
					gestureDetector.onTouchEvent(e);

				switch (e.getActionMasked())
				{
					case MotionEvent.ACTION_DOWN:
						startX = e.getRawX();
						startY = e.getRawY();
						offsetX = container.getX() - startX;
						offsetY = container.getY() - startY;
						dragging = false;
						v.onTouchEvent(e);
						break;
					case MotionEvent.ACTION_MOVE:
						if (!dragging && (Math.abs(e.getRawX() - startX) > touchSlop ||
						                  Math.abs(e.getRawY() - startY) > touchSlop))
						{
							dragging = true;
							MotionEvent cancel = MotionEvent.obtain(e);
							cancel.setAction(MotionEvent.ACTION_CANCEL);
							v.onTouchEvent(cancel);
							cancel.recycle();
						}
						if (dragging)
							container.animate()
							    .x(e.getRawX() + offsetX)
							    .y(e.getRawY() + offsetY)
							    .setDuration(0)
							    .start();
						else
							v.onTouchEvent(e);
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						if (!dragging)
							v.onTouchEvent(e);
						snap();
						break;
				}
				return true;
			}
		};
	}

	private void snap()
	{
		View parent = (View)container.getParent();
		if (parent == null)
			return;

		float px = container.getX(), py = container.getY();
		float pw = parent.getWidth(), ph = parent.getHeight();
		float cw = container.getWidth(), ch = container.getHeight();

		float dLeft = px - insetLeft;
		float dRight = Math.max(0, pw - px - cw - insetRight);
		float dTop = py - insetTop;
		float dBottom = Math.max(0, ph - py - ch - insetBottom);
		float min = Math.min(Math.min(dLeft, dRight), Math.min(dTop, dBottom));

		if (min == dLeft)
			snappedEdge = Edge.LEFT;
		else if (min == dRight)
			snappedEdge = Edge.RIGHT;
		else if (min == dTop)
			snappedEdge = Edge.TOP;
		else
			snappedEdge = Edge.BOTTOM;

		int orientation = (snappedEdge == Edge.LEFT || snappedEdge == Edge.RIGHT)
		                      ? LinearLayout.VERTICAL
		                      : LinearLayout.HORIZONTAL;
		container.setOrientation(orientation);
		buttons.setOrientation(orientation);

		container.post(() -> {
			float w = container.getWidth(), h = container.getHeight();
			float tx = container.getX(), ty = container.getY();
			switch (snappedEdge)
			{
				case LEFT:
					tx = insetLeft;
					break;
				case RIGHT:
					tx = pw - w - insetRight;
					break;
				case TOP:
					ty = insetTop;
					break;
				case BOTTOM:
					ty = ph - h - insetBottom;
					break;
			}
			tx = Math.max(insetLeft, Math.min(tx, pw - w - insetRight));
			ty = Math.max(insetTop, Math.min(ty, ph - h - insetBottom));
			if (snappedEdge == Edge.LEFT || snappedEdge == Edge.RIGHT)
			{
				float available = ph - insetTop - insetBottom - h;
				snappedFraction =
				    available > 0 ? Math.max(0f, Math.min(1f, (ty - insetTop) / available)) : 0.5f;
			}
			else
			{
				float available = pw - insetLeft - insetRight - w;
				snappedFraction =
				    available > 0 ? Math.max(0f, Math.min(1f, (tx - insetLeft) / available)) : 0.5f;
			}
			container.animate().x(tx).y(ty).setDuration(250).start();
		});
	}
}
