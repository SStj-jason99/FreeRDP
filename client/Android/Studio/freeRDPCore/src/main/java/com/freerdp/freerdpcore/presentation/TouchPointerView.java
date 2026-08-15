/*
   Android Touch Pointer view

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz
   Copyright 2026 Ibrahim Sevinc <ibrahim.sevinc.mail@gmail.com>

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.freerdp.freerdpcore.R;

// Full-screen overlay hosting a draggable touch-pointer button cluster.
public class TouchPointerView extends FrameLayout
{
	private static final float SCROLL_DELTA = 10.0f;
	private static final int LONG_PRESS_MS = 500;

	private Context context;
	// Puck drag is amplified by this factor so a full-screen cursor move doesn't require a
	// physical finger drag longer than the screen itself -- without this, reaching the far
	// edge means lifting and re-dragging several times (finger runs out of room on-screen
	// before the cursor does), same as any trackpad without pointer acceleration. Configurable
	// via settings (see refreshFromSettings()); this default only matters before that first
	// runs.
	private float dragGain = 1.4f;

	private ViewGroup cluster;
	private ImageView cursor;
	private ImageButton scrollButton;

	private TouchPointerListener listener = null;

	private float density;
	private int touchSlop;
	private boolean placed = false;

	// The click/move hotspot -- decoupled from the cluster's own on-screen position (see
	// setPositions()) so it can reach every pixel from 0 to the overlay's edge, even though
	// the cluster itself (which carries the drag puck and all the other buttons) is kept
	// fully on-screen so it's never unreachable.
	private float hotspotX, hotspotY;
	// The cursor glyph's base translation that aligns its hotspot pixel with the cluster's
	// own origin (set in setRemoteCursor()); setPositions() adds the extra offset needed to
	// visually pull the glyph the rest of the way out to the true hotspot position.
	private float cursorBaseTx, cursorBaseTy;

	// puck drag state
	private float downRawX, downRawY, startHotspotX, startHotspotY;
	private boolean dragging = false;
	private boolean holdDragging = false;

	// scroll state
	private float scrollLastRawY;
	private float scrollBaseHeight;
	private ValueAnimator pillAnimator;

	private int cursorTint;

	private final Handler uiHandler = new Handler(Looper.getMainLooper());
	private final Runnable longPress = () ->
	{
		if (!dragging && !holdDragging)
		{
			holdDragging = true;
			sendLeft(true);
		}
	};

	public TouchPointerView(Context context)
	{
		this(context, null);
	}

	public TouchPointerView(Context context, AttributeSet attrs)
	{
		this(context, attrs, 0);
	}

	public TouchPointerView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initTouchPointer(context);
	}

	private void initTouchPointer(Context context)
	{
		this.context = context;
		density = getResources().getDisplayMetrics().density;
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		cursorTint = ContextCompat.getColor(context, R.color.tp_icon);
		setClipChildren(false);

		LayoutInflater.from(context).inflate(R.layout.touch_pointer, this, true);
		cluster = findViewById(R.id.tp_cluster);
		cursor = findViewById(R.id.tp_cursor);
		scrollButton = findViewById(R.id.tp_scroll);

		findViewById(R.id.tp_puck).setOnTouchListener((v, e) -> onPuckTouch(e));
		scrollButton.setOnTouchListener((v, e) -> onScrollTouch(e));

		findViewById(R.id.tp_close).setOnClickListener(v -> {
			if (listener != null)
				listener.onTouchPointerClose();
		});
		findViewById(R.id.tp_rclick).setOnClickListener(v -> {
			int[] h = hotspot();
			if (listener != null)
			{
				listener.onTouchPointerRightClick(h[0], h[1], true);
				listener.onTouchPointerRightClick(h[0], h[1], false);
			}
		});
		findViewById(R.id.tp_reset).setOnClickListener(v -> {
			if (listener != null)
				listener.onTouchPointerResetScrollZoom();
		});
		findViewById(R.id.tp_keyboard).setOnClickListener(v -> {
			if (listener != null)
				listener.onTouchPointerToggleKeyboard();
		});
		findViewById(R.id.tp_ext_keyboard).setOnClickListener(v -> {
			if (listener != null)
				listener.onTouchPointerToggleExtKeyboard();
		});

		refreshFromSettings();
	}

	// Re-reads the opacity/sensitivity settings and re-applies them; call whenever the pad is
	// about to become visible so a change made in Settings shows up without needing to
	// recreate the session (see SessionInputManager.toggleTouchPointer()).
	public void refreshFromSettings()
	{
		dragGain = ApplicationSettingsActivity.getTouchPointerSensitivity(context);

		float opacity = ApplicationSettingsActivity.getTouchPointerOpacity(context);
		for (int i = 0; i < cluster.getChildCount(); i++)
		{
			View child = cluster.getChildAt(i);
			if (child != cursor) // the cursor glyph itself stays fully opaque -- it's the
				                 // actual pointer position, not part of "the pad"
				child.setAlpha(opacity);
		}
	}

	public void setTouchPointerListener(TouchPointerListener listener)
	{
		this.listener = listener;
	}

	public int getPointerWidth()
	{
		return cluster.getWidth() > 0
		    ? cluster.getWidth()
		    : getResources().getDimensionPixelSize(R.dimen.tp_cluster_size);
	}

	public int getPointerHeight()
	{
		return cluster.getHeight() > 0
		    ? cluster.getHeight()
		    : getResources().getDimensionPixelSize(R.dimen.tp_cluster_size);
	}

	public float[] getPointerPosition()
	{
		return new float[] { cluster.getX(), cluster.getY() };
	}

	// click hotspot == cursor tip position, in overlay coords (see setPositions())
	private int[] hotspot()
	{
		return new int[] { (int)hotspotX, (int)hotspotY };
	}

	private void sendLeft(boolean down)
	{
		int[] h = hotspot();
		if (listener != null)
			listener.onTouchPointerLeftClick(h[0], h[1], down);
	}

	private void sendMove()
	{
		int[] h = hotspot();
		if (listener != null)
			listener.onTouchPointerMove(h[0], h[1]);
	}

	// desiredX/Y: where the user wants the hotspot -- may reach every pixel from 0 to the
	// overlay's edge (clamped only to that). The cluster itself (drag puck, close button,
	// etc.) is clamped more tightly, to (edge - cluster size), so it always stays fully
	// on-screen and reachable even when the hotspot is pinned to an edge the cluster box
	// can't fully occupy. The cursor glyph is then pulled the rest of the way out to the
	// true hotspot position, visually detaching from the cluster body if needed -- see
	// setRemoteCursor() for cursorBaseTx/Ty.
	private void setPositions(float desiredX, float desiredY)
	{
		float overlayW = getWidth();
		float overlayH = getHeight();
		hotspotX = clamp(desiredX, 0, overlayW);
		hotspotY = clamp(desiredY, 0, overlayH);

		float clusterX = clamp(desiredX, 0, Math.max(0, overlayW - cluster.getWidth()));
		float clusterY = clamp(desiredY, 0, Math.max(0, overlayH - cluster.getHeight()));
		cluster.setTranslationX(clusterX);
		cluster.setTranslationY(clusterY);

		cursor.setTranslationX(cursorBaseTx + (hotspotX - clusterX));
		cursor.setTranslationY(cursorBaseTy + (hotspotY - clusterY));
	}

	private static float clamp(float v, float lo, float hi)
	{
		return v < lo ? lo : (v > hi ? hi : v);
	}

	@Override protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		super.onLayout(changed, l, t, r, b);
		if (!placed && getWidth() > 0 && cluster.getWidth() > 0)
		{
			placed = true;
			setPositions((getWidth() - cluster.getWidth()) / 2.0f,
			            (getHeight() - cluster.getHeight()) / 2.0f);
		}
		else
		{
			setPositions(hotspotX, hotspotY);
		}
	}

	private boolean onPuckTouch(MotionEvent e)
	{
		switch (e.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:
				downRawX = e.getRawX();
				downRawY = e.getRawY();
				startHotspotX = hotspotX;
				startHotspotY = hotspotY;
				dragging = false;
				holdDragging = false;
				uiHandler.postDelayed(longPress, LONG_PRESS_MS);
				return true;
			case MotionEvent.ACTION_MOVE:
			{
				float dx = e.getRawX() - downRawX;
				float dy = e.getRawY() - downRawY;
				if (!dragging && Math.hypot(dx, dy) > touchSlop)
				{
					dragging = true;
					if (!holdDragging)
						uiHandler.removeCallbacks(longPress);
				}
				if (dragging || holdDragging)
				{
					setPositions(startHotspotX + dx * dragGain, startHotspotY + dy * dragGain);
					sendMove();
				}
				return true;
			}
			case MotionEvent.ACTION_UP:
				uiHandler.removeCallbacks(longPress);
				if (holdDragging)
				{
					sendLeft(false);
					holdDragging = false;
				}
				else if (!dragging)
				{
					// tap -> left click (two quick taps register as a double-click)
					sendLeft(true);
					sendLeft(false);
				}
				if (listener != null)
					listener.onTouchPointerMoveEnd();
				return true;
			case MotionEvent.ACTION_CANCEL:
				uiHandler.removeCallbacks(longPress);
				if (holdDragging)
				{
					sendLeft(false);
					holdDragging = false;
				}
				if (listener != null)
					listener.onTouchPointerMoveEnd();
				return true;
		}
		return false;
	}

	private boolean onScrollTouch(MotionEvent e)
	{
		switch (e.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:
				scrollLastRawY = e.getRawY();
				scrollButton.setActivated(true);
				scrollButton.bringToFront();
				morphScroll(true);
				return true;
			case MotionEvent.ACTION_MOVE:
			{
				float dy = e.getRawY() - scrollLastRawY;
				if (dy > SCROLL_DELTA)
				{
					if (listener != null)
						listener.onTouchPointerScroll(true);
					scrollLastRawY = e.getRawY();
				}
				else if (dy < -SCROLL_DELTA)
				{
					if (listener != null)
						listener.onTouchPointerScroll(false);
					scrollLastRawY = e.getRawY();
				}
				return true;
			}
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				scrollButton.setActivated(false);
				morphScroll(false);
				return true;
		}
		return false;
	}

	// grow the scroll button into a tall pill (covering its neighbours) and back
	private void morphScroll(boolean expand)
	{
		if (scrollBaseHeight == 0)
			scrollBaseHeight = scrollButton.getHeight();
		float target = expand ? getResources().getDimensionPixelSize(R.dimen.tp_cluster_size)
		                      : scrollBaseHeight;
		if (pillAnimator != null)
			pillAnimator.cancel();
		pillAnimator = ValueAnimator.ofFloat(scrollButton.getHeight(), target);
		pillAnimator.setDuration(140);
		pillAnimator.addUpdateListener(a -> {
			float h = (float)a.getAnimatedValue();
			ViewGroup.LayoutParams lp = scrollButton.getLayoutParams();
			lp.height = Math.round(h);
			scrollButton.setLayoutParams(lp);
			scrollButton.setTranslationY(-(h - scrollBaseHeight) / 2.0f);
		});
		pillAnimator.start();
	}

	// Set the real remote cursor bitmap (null clears to the fallback); never recycled.
	public void setRemoteCursor(int[] pixels, int width, int height, int hotX, int hotY)
	{
		ViewGroup.LayoutParams lp = cursor.getLayoutParams();
		if (pixels == null || width <= 0 || height <= 0)
		{
			cursor.setImageResource(R.drawable.ic_cursor);
			ImageViewCompat.setImageTintList(cursor, ColorStateList.valueOf(cursorTint));
			int s = getResources().getDimensionPixelSize(R.dimen.tp_cursor_size);
			lp.width = s;
			lp.height = s;
			cursor.setLayoutParams(lp);
			cursorBaseTx = 0;
			cursorBaseTy = 0;
		}
		else
		{
			Bitmap bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
			float scale = 40 * density / height;
			if (scale < 1.2f)
				scale = 1.2f;
			if (scale > 3.0f)
				scale = 3.0f;
			ImageViewCompat.setImageTintList(cursor, null);
			// filterBitmap=false -> nearest-neighbour scaling keeps the small cursor crisp
			BitmapDrawable bd = new BitmapDrawable(getResources(), bmp);
			bd.setFilterBitmap(false);
			cursor.setImageDrawable(bd);
			lp.width = Math.round(width * scale);
			lp.height = Math.round(height * scale);
			cursor.setLayoutParams(lp);
			// base offset aligns the bitmap's hotspot pixel with the cluster's own origin;
			// setPositions() layers the extra offset that pulls it out to the true hotspot
			cursorBaseTx = -hotX * scale;
			cursorBaseTy = -hotY * scale;
		}
		// Re-apply so the cursor glyph's on-screen translation reflects the new base offset
		// immediately, without waiting for the next drag/layout to call setPositions().
		setPositions(hotspotX, hotspotY);
	}

	// touch pointer listener - triggered when an action field is hit
	public interface TouchPointerListener
	{
		void onTouchPointerClose();

		void onTouchPointerLeftClick(int x, int y, boolean down);

		void onTouchPointerRightClick(int x, int y, boolean down);

		void onTouchPointerMove(int x, int y);

		void onTouchPointerMoveEnd();

		void onTouchPointerScroll(boolean down);

		void onTouchPointerToggleKeyboard();

		void onTouchPointerToggleExtKeyboard();

		void onTouchPointerResetScrollZoom();
	}
}
