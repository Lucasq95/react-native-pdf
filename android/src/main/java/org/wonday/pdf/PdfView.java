/**
 * Copyright (c) 2017-present, Wonday (@wonday.org)
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.wonday.pdf;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.os.ParcelFileDescriptor;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.graphics.PointF;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Point;
import javax.annotation.Nullable;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnErrorListener;
import com.github.barteksc.pdfviewer.listener.OnRenderListener;
import com.github.barteksc.pdfviewer.listener.OnTapListener;
import com.github.barteksc.pdfviewer.listener.OnDrawListener;
import com.github.barteksc.pdfviewer.listener.OnPageScrollListener;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.github.barteksc.pdfviewer.util.Constants;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.common.logging.FLog;
import com.facebook.react.common.ReactConstants;

import static java.lang.String.format;
import java.lang.ClassCastException;

import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.PdfDocument;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class PdfView extends PDFView implements OnPageChangeListener,OnLoadCompleteListener,OnErrorListener,OnTapListener,OnDrawListener,OnPageScrollListener {
    private ThemedReactContext context;
    private int page = 1;               // start from 1
    private boolean horizontal = false;
    private float scale = 1;
    private float minScale = 1;
    private float maxScale = 3;
    private String asset;
    private String path;
    private int spacing = 0;
    private String password = "";
    private boolean enableAntialiasing = true;
    private boolean enableAnnotationRendering = true;

    private boolean enablePaging = false;
    private boolean autoSpacing = false;
    private boolean pageFling = false;
    private boolean pageSnap = false;
    private FitPolicy fitPolicy = FitPolicy.WIDTH;

    private static PdfView instance = null;

    private float lastPageWidth = 0;
    private float lastPageHeight = 0;

    private File file = null;

    public PdfView(ThemedReactContext context, AttributeSet set) {
        super(context,set);
        this.context = context;
        this.instance = this;
    }

    @Override
    public void onPageChanged(int page, int numberOfPages) {
        // pdf lib page start from 0, convert it to our page (start from 1)
        page = page+1;
        this.page = page;
        showLog(format("%s %s / %s", path, page, numberOfPages));

        WritableMap event = Arguments.createMap();
        event.putString("message", "pageChanged|"+page+"|"+numberOfPages);
        ReactContext reactContext = (ReactContext)this.getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
            this.getId(),
            "topChange",
            event
         );
    }

    @Override
    public void loadComplete(int numberOfPages) {

        int page = this.page - 1;
        float width = this.instance.getPageSize(page).getWidth();
        float height = this.instance.getPageSize(page).getHeight();

        this.zoomTo(this.scale);
        WritableMap event = Arguments.createMap();

        //create a new jason Object for the TableofContents
        Gson gson = new Gson();
        event.putString("message", "loadComplete|"+numberOfPages+"|"+width+"|"+height+"|"+gson.toJson(this.getTableOfContents()));
        ReactContext reactContext = (ReactContext)this.getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
            this.getId(),
            "topChange",
            event
         );

        //Log.e("ReactNative", gson.toJson(this.getTableOfContents()));

    }

    @Override
    public void onError(Throwable t){
        WritableMap event = Arguments.createMap();
        if (t.getMessage().contains("Password required or incorrect password")) {
            event.putString("message", "error|Password required or incorrect password.");
        } else {
            event.putString("message", "error|"+t.getMessage());
        }

        ReactContext reactContext = (ReactContext)this.getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
            this.getId(),
            "topChange",
            event
         );
    }

    @Override
    public void onPageScrolled(int page, float positionOffset){

        // maybe change by other instance, restore zoom setting
        Constants.Pinch.MINIMUM_ZOOM = this.minScale;
        Constants.Pinch.MAXIMUM_ZOOM = this.maxScale;

    }

    // receives device x and device y as parameters
    public void emitDeviceToCoordsEvent(int x, int y, int containerWidth, int containerHeight) {

      float pdfX = 0;
      float pdfY = 0;

      int page = this.page - 1;
      int startX = 0;
      int startY = 0;

      int pageHeight = (int)this.instance.getPageSize(page).getHeight();
      int pageWidth = (int)this.instance.getPageSize(page).getWidth();

      int currentYOffset = (int)this.instance.getCurrentYOffset() + (page * pageHeight);
      int currentXOffset = (int)this.instance.getCurrentXOffset();
      int spacing = this.instance.getPageSpacing(page);
      int sizeX = Math.round(this.instance.getPageSize(page).getWidth());
      int sizeY = Math.round(this.instance.getPageSize(page).getHeight());
      int rotate = 0;

      // Log.d("currentOffset", "------------------------");
      // Log.d("currentOffset contaniner width", String.valueOf(pageWidth)+" "+String.valueOf(containerWidth));
      // Log.d("currentOffset contaniner height", String.valueOf(pageHeight)+" "+String.valueOf(containerHeight));
      if(spacing > 0 && (containerHeight > pageHeight)) {
        startY = startY + spacing;
      }

      if(spacing > 0 && (containerWidth > pageWidth)) {
        startX = startX + spacing;
      }

      if(currentXOffset < 0 && (containerWidth < pageWidth)) {
        startX = startX + currentXOffset;
      }

      if(currentYOffset < 0 && (containerHeight < pageHeight)) {
        startY = startY + currentYOffset;
      }

      PointF mapped = this.instance.mapDeviceCoordsToPage(page, startX, startY, sizeX,
                                                  sizeY, rotate, x, y);
      pdfX = mapped.x;
      pdfY = mapped.y;
      // Log.d("currentOffset Y ", String.valueOf(pdfY));
      // Log.d("currentOffset X ", String.valueOf(pdfX));
      // Log.d("currentOffset spacing ", String.valueOf(spacing));
      // Log.d("currentOffset start X ", String.valueOf(startX));
      // Log.d("currentOffset start Y ", String.valueOf(startY));
      // Log.d("currentOffset start pageHeight ", String.valueOf(pageHeight));
      // Log.d("currentOffset start pageWidth ", String.valueOf(pageWidth));
      // Log.d("currentOffset", "------------------------");

      WritableMap event = Arguments.createMap();
      event.putString("message", "pageCoords|"+this.page+"|"+pdfX+"|"+pdfY+"|"+pageWidth+"|"+pageHeight);

      ReactContext reactContext = (ReactContext)this.getContext();
      reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
          this.getId(),
          "topChange",
          event
       );
    }

    @Override
    public boolean onTap(MotionEvent e){

        // maybe change by other instance, restore zoom setting
        Constants.Pinch.MINIMUM_ZOOM = this.minScale;
        Constants.Pinch.MAXIMUM_ZOOM = this.maxScale;

        int x = Math.round(e.getX());
        int y = Math.round(e.getY());

        Log.d("Screen Coordinates ", x + ", " + y);

        float pdfX = 0;
        float pdfY = 0;

        int page = this.page - 1;
        int startX = 0;
        int startY = 0;
        int sizeX = Math.round(this.instance.getPageSize(page).getWidth());
        int sizeY = Math.round(this.instance.getPageSize(page).getHeight());
        int rotate = 0;
        PointF mapped = this.instance.mapDeviceCoordsToPage(page, startX, startY, sizeX,
                                                    sizeY, rotate, x, y);
        pdfX = mapped.x;
        pdfY = mapped.y;

        WritableMap event = Arguments.createMap();
        event.putString("message", "pageSingleTap|"+page+"|"+pdfX+"|"+pdfY);

        ReactContext reactContext = (ReactContext)this.getContext();
        reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
            this.getId(),
            "topChange",
            event
         );

        // process as tap
         return true;

    }

    @Override
    public void onLayerDrawn(Canvas canvas, float pageWidth, float pageHeight, int displayedPage){

        if (lastPageWidth>0 && lastPageHeight>0 && (pageWidth!=lastPageWidth || pageHeight!=lastPageHeight)) {

            // maybe change by other instance, restore zoom setting
            Constants.Pinch.MINIMUM_ZOOM = this.minScale;
            Constants.Pinch.MAXIMUM_ZOOM = this.maxScale;

            WritableMap event = Arguments.createMap();
            event.putString("message", "scaleChanged|"+(pageWidth/lastPageWidth));

            ReactContext reactContext = (ReactContext)this.getContext();
            reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                this.getId(),
                "topChange",
                event
             );
        }

        lastPageWidth = pageWidth;
        lastPageHeight = pageHeight;

    }


    public void drawPdf(){
        showLog(format("drawPdf path:%s %s", this.path, this.page));

        if (this.path != null){
              // set scale
              this.setMinZoom(this.minScale);
              this.setMaxZoom(this.maxScale);
              this.setMidZoom((this.maxScale+this.minScale)/2);
              Constants.Pinch.MINIMUM_ZOOM = this.minScale;
              Constants.Pinch.MAXIMUM_ZOOM = this.maxScale;

              this.fromUri(getURI(this.path))
                  .defaultPage(this.page-1)
                  .swipeHorizontal(this.horizontal)
                  .onPageChange(this)
                  .onLoad(this)
                  .onError(this)
                  .onTap(this)
                  .onDraw(this)
                  .onPageScroll(this)
                  .spacing(this.spacing)
                  .password(this.password)
                  .enableAntialiasing(this.enableAntialiasing)
                  .pageFitPolicy(this.fitPolicy)
                  .pageSnap(this.pageSnap)
                  .autoSpacing(this.autoSpacing)
                  .pageFling(this.pageFling)
                  .enableAnnotationRendering(this.enableAnnotationRendering)
                  .load();
        }
    }

    public void setPath(String path) {
        this.path = path;
    }

    // page start from 1
    public void setPage(int page) {
        this.page = page>1?page:1;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    public void setHorizontal(boolean horizontal) {
        this.horizontal = horizontal;
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setEnableAntialiasing(boolean enableAntialiasing) {
        this.enableAntialiasing = enableAntialiasing;
    }

    public void setEnableAnnotationRendering(boolean enableAnnotationRendering) {
        this.enableAnnotationRendering = enableAnnotationRendering;
    }

    public void setEnablePaging(boolean enablePaging) {
        this.enablePaging = enablePaging;
        if (this.enablePaging) {
            this.autoSpacing = true;
            this.pageFling = true;
            this.pageSnap = true;
        } else {
            this.autoSpacing = false;
            this.pageFling = false;
            this.pageSnap = false;
        }
    }

    public void setFitPolicy(int fitPolicy) {
        switch(fitPolicy){
            case 0:
                this.fitPolicy = FitPolicy.WIDTH;
                break;
            case 1:
                this.fitPolicy = FitPolicy.HEIGHT;
                break;
            case 2:
            default:
            {
                this.fitPolicy = FitPolicy.BOTH;
                break;
            }
        }

    }

    private void showLog(final String str) {
        Log.d("PdfView", str);
    }

    private Uri getURI(final String uri) {
        Uri parsed = Uri.parse(uri);

        if (parsed.getScheme() == null || parsed.getScheme().isEmpty()) {
          return Uri.fromFile(new File(uri));
        }
        return parsed;
    }
}
