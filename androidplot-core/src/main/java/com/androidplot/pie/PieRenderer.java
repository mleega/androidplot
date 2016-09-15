/*
 * Copyright 2015 AndroidPlot.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.androidplot.pie;

import android.graphics.*;

import com.androidplot.exception.PlotRenderException;
import com.androidplot.ui.SeriesAndFormatter;
import com.androidplot.ui.SeriesRenderer;
import com.androidplot.ui.RenderStack;

import java.util.List;

/**
 * Basic renderer for drawing pie charts.
 */
public class PieRenderer extends SeriesRenderer<PieChart, Segment, SegmentFormatter> {

    // starting angle to use when drawing the first radial line of the first segment.
    @SuppressWarnings("FieldCanBeLocal")
    private float startDeg = 0;
    private float endDeg = 360;

    // TODO: express donut in units other than px.
    private float donutSize = 0.5f;
    private DonutMode donutMode = DonutMode.PERCENT;

    public enum DonutMode {
        PERCENT,
        DP,
        PIXELS
    }

    public PieRenderer(PieChart plot) {
        super(plot);
    }

    public float getRadius(RectF rect) {
    	return  rect.width() < rect.height() ? rect.width() / 2 : rect.height() / 2;
    }

    @Override
    public void onRender(Canvas canvas, RectF plotArea, Segment series, SegmentFormatter formatter, RenderStack stack) throws PlotRenderException {

        // This renderer renders all series in one shot, so exclude any remaining series
        // from causing subsequent invocations of onRender:
        stack.disable(getClass());

        float radius = getRadius(plotArea);
        PointF origin = new PointF(plotArea.centerX(), plotArea.centerY());
        
        double[] values = getValues();
        double scale = calculateScale(values);
        float offset = startDeg;

        RectF rec = new RectF(origin.x - radius, origin.y - radius, origin.x + radius, origin.y + radius);
        
        int i = 0;
        for (SeriesAndFormatter<Segment, ? extends SegmentFormatter> sfPair : getSeriesAndFormatterList()) {
            float lastOffset = offset;
            float sweep = (float) (scale * (values[i]) * 360);
            offset += sweep;
            drawSegment(canvas, rec, sfPair.getSeries(), sfPair.getFormatter(), radius, lastOffset, sweep);
            i++;
        }
    }

    protected void drawSegment(Canvas canvas, RectF bounds, Segment seg, SegmentFormatter f,
                               float rad, float startAngle, float sweep) {
        canvas.save();
        startAngle = startAngle + f.getRadialInset();
        sweep = sweep - (f.getRadialInset() * 2);

        // midpoint angle between startAngle and endAngle
        final float halfSweepEndAngle = startAngle + (sweep/2);

        PointF translated = calculateLineEnd(
                bounds.centerX(), bounds.centerY(), f.getOffset(), halfSweepEndAngle);

        final float cx = translated.x;
        final float cy = translated.y;

        float donutSizePx;
        switch(donutMode) {
            case PERCENT:
                donutSizePx = donutSize * rad;
                break;
            case PIXELS:
                donutSizePx = (donutSize > 0)?donutSize:(rad + donutSize);
                break;
            default:
                throw new UnsupportedOperationException("Not yet implemented.");
        }

        final float outerRad = rad - f.getOuterInset();
        final float innerRad = donutSizePx == 0 ? 0 :  donutSizePx + f.getInnerInset();
        //final float outerRad = rad;
        //final float innerRad = donutSizePx;

        // do we have a pie chart of less than 100%
        if(Math.abs(sweep - 360f) > Float.MIN_VALUE) {
            // vertices of the first radial:
            PointF r1Outer = calculateLineEnd(cx, cy, outerRad, startAngle);
            PointF r1Inner = calculateLineEnd(cx, cy, innerRad, startAngle);

            // vertices of the second radial:
            PointF r2Outer = calculateLineEnd(cx, cy, outerRad, startAngle + sweep);
            PointF r2Inner = calculateLineEnd(cx, cy, innerRad, startAngle + sweep);

            Path clip = new Path();

            // outer arc:
            // leave plenty of room on the outside for stroked borders;
            // necessary because the clipping border is ugly
            // and cannot be easily anti aliased.  Really we only care about masking off the
            // radial edges.
            clip.arcTo(new RectF(bounds.left - outerRad,
                    bounds.top - outerRad,
                    bounds.right + outerRad,
                    bounds.bottom + outerRad),
                    startAngle, sweep);
            clip.lineTo(cx, cy);
            clip.close();
            canvas.clipPath(clip);

            Path p = new Path();

            // outer arc:
            p.arcTo(new RectF(
                    cx - outerRad,
                    cy - outerRad,
                    cx + outerRad,
                    cy + outerRad)
                    , startAngle, sweep);
            p.lineTo(r2Inner.x, r2Inner.y);

            // inner arc:
            // sweep back to original angle:
            p.arcTo(new RectF(
                    cx - innerRad,
                    cy - innerRad,
                    cx + innerRad,
                    cy + innerRad),
                    startAngle + sweep, -sweep);

            p.close();

            // fill segment:
            canvas.drawPath(p, f.getFillPaint());

            // draw radial lines
            canvas.drawLine(r1Inner.x, r1Inner.y, r1Outer.x, r1Outer.y, f.getRadialEdgePaint());
            canvas.drawLine(r2Inner.x, r2Inner.y, r2Outer.x, r2Outer.y, f.getRadialEdgePaint());
        }
        else {
            canvas.save(Canvas.CLIP_SAVE_FLAG);
            Path chart = new Path();
            chart.addCircle(cx, cy, outerRad, Path.Direction.CW);
            Path inside = new Path();
            inside.addCircle(cx, cy, innerRad, Path.Direction.CW);
            canvas.clipPath(inside, Region.Op.DIFFERENCE);
            canvas.drawPath(chart, f.getFillPaint());
            canvas.restore();
        }

        // draw inner line:
        canvas.drawCircle(cx, cy, innerRad, f.getInnerEdgePaint());

        // draw outer line:
        canvas.drawCircle(cx, cy, outerRad, f.getOuterEdgePaint());
        canvas.restore();

        PointF labelOrigin = calculateLineEnd(cx, cy,
                (outerRad-((outerRad- innerRad)/2)), halfSweepEndAngle);

        // TODO: move segment labelling outside the segment drawing loop
        // TODO: so that the labels will not be clipped by the edge of the next
        // TODO: segment being drawn.
        drawSegmentLabel(canvas, labelOrigin, seg, f);
    }

    protected void drawSegmentLabel(Canvas canvas, PointF origin,
                                    Segment seg, SegmentFormatter f) {
        canvas.drawText(seg.getTitle(), origin.x, origin.y, f.getLabelPaint());

    }

    @Override
    protected void doDrawLegendIcon(Canvas canvas, RectF rect, SegmentFormatter formatter) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }

    /**
     * Determines how many counts there are per cent of whatever the
     * pie chart is displaying as a fraction, 1 being 100%.
     */
    protected double calculateScale(double[] values) {
        double total = 0;
        for (int i = 0; i < values.length; i++) {
			total += values[i];
		}

        return (1d / total);
    }
    
	protected double[] getValues() {
        List<SeriesAndFormatter<Segment, ? extends SegmentFormatter>> seriesList = getSeriesAndFormatterList();
		double[] result = new double[seriesList.size()];
		int i = 0;
		for (SeriesAndFormatter<Segment, ? extends SegmentFormatter> sfPair : seriesList) {
			result[i] = sfPair.getSeries().getValue().doubleValue();
			i++;
		}
		return result;
	}

    protected PointF calculateLineEnd(float x, float y, float rad, float deg) {
        return calculateLineEnd(new PointF(x, y), rad, deg);
    }

    protected PointF calculateLineEnd(PointF origin, float rad, float deg) {

        double radians = deg * Math.PI / 180F;
        double x = rad * Math.cos(radians);
        double y = rad * Math.sin(radians);

        // convert to screen space:
        return new PointF(origin.x + (float) x, origin.y + (float) y);
    }

    public void setDonutSize(float size, DonutMode mode) {
        switch(mode) {
            case PERCENT:
                if(size < 0 || size > 1) {
                    throw new IllegalArgumentException(
                            "Size parameter must be between 0 and 1 when operating in PERCENT mode.");
                }
                break;
            case PIXELS:
            	break;
            default:
                throw new UnsupportedOperationException("Not yet implemented.");
        }
        donutMode = mode;
        donutSize = size;
    }

    /**
     * Retrieve the segment containing the specified point.  This current implementation
     * only matches against angle; clicks outside of the pie/donut inner/outer boundaries
     * will still trigger a match on the segment whose begining and ending angle contains
     * the angle of the line drawn between the pie chart's center point and the clicked point.
     * @param point The clicked point
     * @return Segment containing the clicked point.
     */
    public Segment getContainingSegment(PointF point) {

        RectF plotArea = getPlot().getPie().getWidgetDimensions().marginatedRect;
        // figure out the angle in degrees of the line between the clicked point
        // and the origin of the plotArea:
        PointF origin = new PointF(plotArea.centerX(), plotArea.centerY());
        float dx = point.x - origin.x;
        float dy = point.y - origin.y;
        double theta = Math.atan2(dy, dx);
        double angle = (theta * (180f/Math.PI));
        if(angle < 0) {
            // convert angle to 0-360 range with 0 being in the
            // traditional "westerly" orientation:
            angle += 360;
        }

        // find the segment whose starting and ending angle (degs) contains
        // the angle calculated above
        List<SeriesAndFormatter<Segment, ? extends SegmentFormatter>> seriesList = getSeriesAndFormatterList();
        int i = 0;
        double[] values = getValues();
        double scale = calculateScale(values);
        float offset = startDeg;
        for (SeriesAndFormatter<Segment, ? extends SegmentFormatter> sfPair : seriesList) {
            float lastOffset = offset;
            float sweep = (float) (scale * (values[i]) * 360);
            offset += sweep;

            if(angle >= lastOffset && angle <= offset) {
                return sfPair.getSeries();
            }
            i++;
        }
        return null;
    }
    
    public void setStartDeg(float deg) {
        startDeg = deg;
    }

    public float getStartDeg() {
        return startDeg;
    }
    
    public void setEndDeg(float deg) {
        endDeg = deg;
    }

    public float getEndDeg() {
        return endDeg;
    }
}
