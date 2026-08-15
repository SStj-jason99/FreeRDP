/*
   Android Magnifier overlay

   Copyright 2026 Ibrahim Sevinc <ibrahim.sevinc.mail@gmail.com>

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

// Full-screen, touch-transparent overlay that draws a small circular magnified preview of the
// remote-desktop bitmap near an ongoing drag/selection, so the user's finger doesn't hide what
// they're about to click/select. Toggled on/off from settings (see
// ApplicationSettingsActivity.getShowMagnifier); the view itself just draws when shown.
public class MagnifierView extends View
{
	public interface ContentSource
	{
		// Bitmap currently shown by the session surface, in native (unscaled) pixels, or null.
		Bitmap getMagnifierContentBitmap();
	}

	private static final float DIAMETER_DP = 110f;
	private static final float MAGNIFICATION = 3.0f;
	private static final float VERTICAL_OFFSET_DP = 90f; // bubble floats above the touch point

	private ContentSource contentSource;
	private boolean visible = false;
	private float anchorX, anchorY; // local coords to hover the bubble above
	private float contentX, contentY; // remote-content-space point being previewed

	private final Paint borderPaint;
	private final Paint bgPaint;
	private final Paint crosshairPaint;
	private final RectF dstRect = new RectF();
	private final Rect srcRect = new Rect();
	private final Path clipPath = new Path();
	private final int[] screenLoc = new int[2];

	private final float radiusPx;
	private final float verticalOffsetPx;
	private final float crosshairSizePx;

	public MagnifierView(Context context)
	{
		this(context, null);
	}

	public MagnifierView(Context context, AttributeSet attrs)
	{
		this(context, attrs, 0);
	}

	public MagnifierView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		float density = getResources().getDisplayMetrics().density;
		radiusPx = DIAMETER_DP * density / 2f;
		verticalOffsetPx = VERTICAL_OFFSET_DP * density;
		crosshairSizePx = 8f * density;

		borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		borderPaint.setStyle(Paint.Style.STROKE);
		borderPaint.setStrokeWidth(2f * density);
		borderPaint.setColor(Color.WHITE);

		bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		bgPaint.setColor(Color.BLACK);

		crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		crosshairPaint.setColor(Color.RED);
		crosshairPaint.setStrokeWidth(1.5f * density);

		setWillNotDraw(false);
		// this overlay never intercepts touches -- it only ever draws
		setClickable(false);
		setFocusable(false);
	}

	public void setContentSource(ContentSource source)
	{
		contentSource = source;
	}

	// screenX/screenY: absolute screen coordinates (e.g. MotionEvent.getRawX/Y(), or another
	// view's local point translated via its own getLocationOnScreen()).
	// contentX/contentY: the matching position in the remote-desktop bitmap to preview.
	public void show(float screenX, float screenY, float contentX, float contentY)
	{
		getLocationOnScreen(screenLoc);
		anchorX = screenX - screenLoc[0];
		anchorY = screenY - screenLoc[1];
		this.contentX = contentX;
		this.contentY = contentY;
		visible = true;
		invalidate();
	}

	public void hide()
	{
		if (!visible)
			return;
		visible = false;
		invalidate();
	}

	@Override public boolean onTouchEvent(android.view.MotionEvent event)
	{
		// pure overlay -- never consume touches, so it can't block anything underneath
		return false;
	}

	@Override protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		if (!visible || contentSource == null)
			return;

		Bitmap bmp = contentSource.getMagnifierContentBitmap();
		if (bmp == null || bmp.getWidth() <= 0 || bmp.getHeight() <= 0)
			return;

		float srcRadius = radiusPx / MAGNIFICATION;
		int left = (int)(contentX - srcRadius);
		int top = (int)(contentY - srcRadius);
		int right = (int)(contentX + srcRadius);
		int bottom = (int)(contentY + srcRadius);
		// shift the crop box (without resizing it) to stay inside the bitmap, so the
		// magnification level stays constant even near the edges
		if (left < 0)
		{
			right -= left;
			left = 0;
		}
		if (top < 0)
		{
			bottom -= top;
			top = 0;
		}
		if (right > bmp.getWidth())
		{
			left -= (right - bmp.getWidth());
			right = bmp.getWidth();
		}
		if (bottom > bmp.getHeight())
		{
			top -= (bottom - bmp.getHeight());
			bottom = bmp.getHeight();
		}
		left = Math.max(0, left);
		top = Math.max(0, top);
		if (right <= left || bottom <= top)
			return;
		srcRect.set(left, top, right, bottom);

		// bubble sits above the anchor point; flips below if that would go off the top edge
		float cx = Math.max(radiusPx, Math.min(anchorX, getWidth() - radiusPx));
		float cy = anchorY - verticalOffsetPx;
		if (cy - radiusPx < 0)
			cy = anchorY + verticalOffsetPx;

		dstRect.set(cx - radiusPx, cy - radiusPx, cx + radiusPx, cy + radiusPx);

		int saveCount = canvas.save();
		clipPath.reset();
		clipPath.addCircle(cx, cy, radiusPx, Path.Direction.CW);
		canvas.clipPath(clipPath);
		canvas.drawRect(dstRect, bgPaint);
		canvas.drawBitmap(bmp, srcRect, dstRect, null);
		canvas.restoreToCount(saveCount);

		canvas.drawCircle(cx, cy, radiusPx, borderPaint);
		canvas.drawLine(cx - crosshairSizePx, cy, cx + crosshairSizePx, cy, crosshairPaint);
		canvas.drawLine(cx, cy - crosshairSizePx, cx, cy + crosshairSizePx, crosshairPaint);
	}
}
