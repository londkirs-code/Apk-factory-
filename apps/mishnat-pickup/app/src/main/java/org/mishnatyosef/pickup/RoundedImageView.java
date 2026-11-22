package org.mishnatyosef.pickup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.ImageView;

/** Rounded clipping and keyboard focus ring supported on API 19 and newer. */
final class RoundedImageView extends ImageView {
    private final Path clip=new Path();
    private final RectF bounds=new RectF();
    private final Paint ringPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringBounds=new RectF();
    private final float radius;
    private boolean ring;
    RoundedImageView(Context context,float radiusPx){super(context);radius=radiusPx;ringPaint.setStyle(Paint.Style.STROKE);}
    void setRing(boolean enabled){ring=enabled;invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);bounds.set(0,0,w,h);clip.reset();
        clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
    }
    @Override protected void onDraw(Canvas canvas){
        int save=canvas.save();canvas.clipPath(clip);super.onDraw(canvas);canvas.restoreToCount(save);
        if(ring){
            float d=getResources().getDisplayMetrics().density;
            ringPaint.setStrokeWidth(7*d);ringPaint.setColor(Ui.P25);
            canvas.drawRoundRect(inset(3.5f*d),radius,radius,ringPaint);
            ringPaint.setStrokeWidth(4*d);ringPaint.setColor(Ui.P800);
            canvas.drawRoundRect(inset(2*d),radius,radius,ringPaint);
        }
    }
    private RectF inset(float by){ringBounds.set(by,by,getWidth()-by,getHeight()-by);return ringBounds;}
}
