package net.osmand.core.android;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import net.osmand.core.jni.*;
import net.osmand.data.LatLon;
import net.osmand.data.QuadPoint;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.helpers.TwoFingerTapDetector;
import net.osmand.plus.render.NativeOsmandLibrary;
import android.app.Activity;
import android.content.Context;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;

public class GLActivity extends Activity {

	static {
		NativeOsmandLibrary.loadLibrary("gnustl_shared");
		NativeOsmandLibrary.loadLibrary("Qt5Core");
		NativeOsmandLibrary.loadLibrary("Qt5Network");
		NativeOsmandLibrary.loadLibrary("Qt5Sql");
		NativeOsmandLibrary.loadLibrary("OsmAndCoreWithJNI");
	}
    private static final String TAG = "OsmAndCoreSample";

    private CoreResourcesFromAndroidAssetsCustom _coreResources;

    private float _displayDensityFactor;
    private int _referenceTileSize;
    private int _rasterTileSize;
    private IMapStylesCollection _mapStylesCollection;
    private ResolvedMapStyle _mapStyle;
    private ObfsCollection _obfsCollection;
    private MapPresentationEnvironment _mapPresentationEnvironment;
    private MapPrimitiviser _mapPrimitiviser;
    private ObfMapObjectsProvider _obfMapObjectsProvider;
    private MapPrimitivesProvider _mapPrimitivesProvider;
    private MapObjectsSymbolsProvider _mapObjectsSymbolsProvider;
    private MapRasterLayerProvider _mapRasterLayerProvider;
	private OnlineRasterMapLayerProvider _onlineMapRasterLayerProvider;
    private IMapRenderer _mapRenderer;
    private GpuWorkerThreadPrologue _gpuWorkerThreadPrologue;
    private GpuWorkerThreadEpilogue _gpuWorkerThreadEpilogue;
    private RenderRequestCallback _renderRequestCallback;
    private QIODeviceLogSink _fileLogSink;
    private RotatedTileBox currentViewport = null;
    
	private GestureDetector gestureDetector;

    
    protected OsmandApplication getApp() {
    	return (OsmandApplication) getApplication();
    }
    
    private boolean afterTwoFingerTap = false;
	TwoFingerTapDetector twoFingerTapDetector = new TwoFingerTapDetector() {
		@Override
		public void onTwoFingerTap() {
			afterTwoFingerTap = true;
			currentViewport.setZoom(currentViewport.getZoom() - 1);
			updateView();
		}
	};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        OsmandSettings st = getApp().getSettings();
        WindowManager mgr = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics dm = new DisplayMetrics();
		mgr.getDefaultDisplay().getMetrics(dm);
		currentViewport = new RotatedTileBox.RotatedTileBoxBuilder().
				setLocation(st.getLastKnownMapLocation().getLatitude(), 
						st.getLastKnownMapLocation().getLongitude()).setZoomAndScale(st.getLastKnownMapZoom(), 0).
						setPixelDimensions(dm.widthPixels, dm.heightPixels).build();
		currentViewport.setDensity(dm.density);
		
		
		
		gestureDetector = new GestureDetector(this, new android.view.GestureDetector.OnGestureListener() {
			
			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				return false;
			}
			
			@Override
			public void onShowPress(MotionEvent e) {
			}
			
			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
				final QuadPoint cp = currentViewport.getCenterPixelPoint();
				final LatLon latlon = currentViewport.getLatLonFromPixel(cp.x + distanceX, cp.y + distanceY);
				currentViewport.setLatLonCenter(latlon.getLatitude(), latlon.getLongitude());
				updateView();
				return false;
			}
			
			@Override
			public void onLongPress(MotionEvent e) {
			}
			
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				return false;
			}
			
			@Override
			public boolean onDown(MotionEvent e) {
				return false;
			}
		});
		gestureDetector.setOnDoubleTapListener(new OnDoubleTapListener() {
			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				return false;
			}
			
			@Override
			public boolean onDoubleTapEvent(MotionEvent e) {
				return false;
			}
			
			@Override
			public boolean onDoubleTap(MotionEvent e) {
				currentViewport.setZoom(currentViewport.getZoom() + 1);
				updateView();
				return true;
			}
		});
        		
        setContentView(R.layout.activity_gl);

        // Get device display density factor
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        _displayDensityFactor = displayMetrics.densityDpi / 160.0f;
        _referenceTileSize = (int)(256 * _displayDensityFactor);
        _rasterTileSize = Integer.highestOneBit(_referenceTileSize - 1) * 2;
        Log.i(TAG, "displayDensityFactor = " + _displayDensityFactor);
        Log.i(TAG, "referenceTileSize = " + _referenceTileSize);
        Log.i(TAG, "rasterTileSize = " + _rasterTileSize);

        Log.i(TAG, "Initializing core...");
        _coreResources = CoreResourcesFromAndroidAssetsCustom.loadFromCurrentApplication(this);
        OsmAndCore.InitializeCore(_coreResources.instantiateProxy());

		File directory =getApp().getAppPath("");
        _fileLogSink = QIODeviceLogSink.createFileLogSink(directory.getAbsolutePath() + "/osmandcore.log");
        Logger.get().addLogSink(_fileLogSink);

        Log.i(TAG, "Going to resolve default embedded style...");
        _mapStylesCollection = new MapStylesCollection();
        _mapStyle = _mapStylesCollection.getResolvedStyleByName("default");
        if (_mapStyle == null)
        {
            Log.e(TAG, "Failed to resolve style 'default'");
            System.exit(0);
        }

        Log.i(TAG, "Going to prepare OBFs collection");
        _obfsCollection = new ObfsCollection();

		Log.i(TAG, "Will load OBFs from " + directory.getAbsolutePath());
        _obfsCollection.addDirectory(directory.getAbsolutePath(), false);

        Log.i(TAG, "Going to prepare all resources for renderer");
        _mapPresentationEnvironment = new MapPresentationEnvironment(
                _mapStyle,
                _displayDensityFactor,
                "en"); //TODO: here should be current locale
        //mapPresentationEnvironment->setSettings(configuration.styleSettings);
        _mapPrimitiviser = new MapPrimitiviser(
                _mapPresentationEnvironment);
        _obfMapObjectsProvider = new ObfMapObjectsProvider(
                _obfsCollection);
        _mapPrimitivesProvider = new MapPrimitivesProvider(
                _obfMapObjectsProvider,
                _mapPrimitiviser,
                _rasterTileSize);
        _mapObjectsSymbolsProvider = new MapObjectsSymbolsProvider(
                _mapPrimitivesProvider,
                _rasterTileSize);
        _mapRasterLayerProvider = new MapRasterLayerProvider_Software(
                _mapPrimitivesProvider);

		_onlineMapRasterLayerProvider = OnlineTileSources.getBuiltIn().createProviderFor("Mapnik (OsmAnd)");

        Log.i(TAG, "Going to create renderer");
        _mapRenderer = OsmAndCore.createMapRenderer(MapRendererClass.AtlasMapRenderer_OpenGLES2);
        if (_mapRenderer == null)
        {
            Log.e(TAG, "Failed to create map renderer 'AtlasMapRenderer_OpenGLES2'");
            System.exit(0);
        }

        AtlasMapRendererConfiguration atlasRendererConfiguration = AtlasMapRendererConfiguration.Casts.upcastFrom(_mapRenderer.getConfiguration());
        atlasRendererConfiguration.setReferenceTileSizeOnScreenInPixels(_referenceTileSize);
        _mapRenderer.setConfiguration(AtlasMapRendererConfiguration.Casts.downcastTo_MapRendererConfiguration(atlasRendererConfiguration));

        _mapRenderer.addSymbolsProvider(_mapObjectsSymbolsProvider);
        updateView();
        /*
        IMapRasterLayerProvider mapnik = OnlineTileSources.getBuiltIn().createProviderFor("Mapnik (OsmAnd)");
        if (mapnik == null)
            Log.e(TAG, "Failed to create mapnik");
        */
        _mapRenderer.setMapLayerProvider(0, _mapRasterLayerProvider);

        _glSurfaceView = (GLSurfaceView) findViewById(R.id.glSurfaceView);
        //TODO:_glSurfaceView.setPreserveEGLContextOnPause(true);
        _glSurfaceView.setEGLContextClientVersion(2);
        _glSurfaceView.setEGLContextFactory(new EGLContextFactory());
        _glSurfaceView.setRenderer(new Renderer());
        _glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

	protected void updateView() {
		_mapRenderer.setAzimuth(0.0f);
		_mapRenderer.setElevationAngle(90);
		_mapRenderer.setTarget(new PointI(currentViewport.getCenter31X(), currentViewport.getCenter31Y()));
		_mapRenderer.setZoom((float)currentViewport.getZoom() + (float)currentViewport.getZoomScale());
	}

	private GLSurfaceView _glSurfaceView;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
    	if (twoFingerTapDetector.onTouchEvent(event)) {
			return true;
		}
    	return gestureDetector.onTouchEvent(event);
    }
    @Override
    protected void onPause() {
        super.onPause();
        _glSurfaceView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        _glSurfaceView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (_mapStylesCollection != null) {
            _mapStylesCollection.delete();
            _mapStylesCollection = null;
        }

        if (_mapStyle != null) {
            _mapStyle.delete();
            _mapStyle = null;
        }

        if (_obfsCollection != null) {
            _obfsCollection.delete();
            _obfsCollection = null;
        }

        if (_mapPresentationEnvironment != null) {
            _mapPresentationEnvironment.delete();
            _mapPresentationEnvironment = null;
        }

        if (_mapPrimitiviser != null) {
            _mapPrimitiviser.delete();
            _mapPrimitiviser = null;
        }

        if (_obfMapObjectsProvider != null) {
            _obfMapObjectsProvider.delete();
            _obfMapObjectsProvider = null;
        }

        if (_mapPrimitivesProvider != null) {
            _mapPrimitivesProvider.delete();
            _mapPrimitivesProvider = null;
        }

        if (_mapObjectsSymbolsProvider != null) {
            _mapObjectsSymbolsProvider.delete();
            _mapObjectsSymbolsProvider = null;
        }

        if (_mapRasterLayerProvider != null) {
            _mapRasterLayerProvider.delete();
            _mapRasterLayerProvider = null;
        }

        if (_mapRenderer != null) {
            _mapRenderer.delete();
            _mapRenderer = null;
        }

        OsmAndCore.ReleaseCore();

        super.onDestroy();
    }

    private class RenderRequestCallback extends MapRendererSetupOptions.IFrameUpdateRequestCallback {
        @Override
        public void method(IMapRenderer mapRenderer) {
            _glSurfaceView.requestRender();
        }
    }

    public class GpuWorkerThreadPrologue extends MapRendererSetupOptions.IGpuWorkerThreadPrologue {
        public GpuWorkerThreadPrologue(EGL10 egl, EGLDisplay eglDisplay, EGLContext context, EGLSurface surface) {
            _egl = egl;
            _eglDisplay = eglDisplay;
            _context = context;
            _eglSurface = surface;
        }

        private final EGL10 _egl;
        private final EGLDisplay _eglDisplay;
        private final EGLContext _context;
        private final EGLSurface _eglSurface;

        @Override
        public void method(IMapRenderer mapRenderer) {
            try {
                if (!_egl.eglMakeCurrent(_eglDisplay, _eglSurface, _eglSurface, _context))
                    Log.e(TAG, "Failed to set GPU worker context active: " + _egl.eglGetError());
            } catch (Exception e) {
                Log.e(TAG, "Failed to set GPU worker context active", e);
            }
        }
    }

    private class GpuWorkerThreadEpilogue extends MapRendererSetupOptions.IGpuWorkerThreadEpilogue {
        public GpuWorkerThreadEpilogue(EGL10 egl) {
            _egl = egl;
        }

        private final EGL10 _egl;

        @Override
        public void method(IMapRenderer mapRenderer) {
            try {
                if (!_egl.eglWaitGL())
                    Log.e(TAG, "Failed to wait for GPU worker context: " + _egl.eglGetError());
            } catch (Exception e) {
                Log.e(TAG, "Failed to wait for GPU worker context", e);
            }
        }
    }

    private class EGLContextFactory implements GLSurfaceView.EGLContextFactory {
        private EGLContext _gpuWorkerContext;
        private EGLSurface _gpuWorkerFakeSurface;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            final String eglExtensions = egl.eglQueryString(display, EGL10.EGL_EXTENSIONS);
            Log.i(TAG, "EGL extensions: " + eglExtensions);
            final String eglVersion = egl.eglQueryString(display, EGL10.EGL_VERSION);
            Log.i(TAG, "EGL version: " + eglVersion);

            Log.i(TAG, "Creating main context...");
            final int[] contextAttribList = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL10.EGL_NONE };

            EGLContext mainContext = null;
            try {
                mainContext = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, contextAttribList);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create main context", e);
            }
            if (mainContext == null || mainContext == EGL10.EGL_NO_CONTEXT) {
                Log.e(TAG, "Failed to create main context: " + egl.eglGetError());
                mainContext = null;
                System.exit(0);
            }

            Log.i(TAG, "Creating GPU worker context...");
            try {
                _gpuWorkerContext = egl.eglCreateContext(
                        display,
                        eglConfig,
                        mainContext,
                        contextAttribList);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create GPU worker context", e);
            }
            if (_gpuWorkerContext == null || _gpuWorkerContext == EGL10.EGL_NO_CONTEXT)
            {
                Log.e(TAG, "Failed to create GPU worker context: " + egl.eglGetError());
                _gpuWorkerContext = null;
            }

            if (_gpuWorkerContext != null)
            {
                Log.i(TAG, "Creating GPU worker fake surface...");
                try {
                    final int[] surfaceAttribList = {
                            EGL10.EGL_WIDTH, 1,
                            EGL10.EGL_HEIGHT, 1,
                            EGL10.EGL_NONE };
                    _gpuWorkerFakeSurface = egl.eglCreatePbufferSurface(display, eglConfig, surfaceAttribList);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create GPU worker fake surface", e);
                }
                if (_gpuWorkerFakeSurface == null || _gpuWorkerFakeSurface == EGL10.EGL_NO_SURFACE)
                {
                    Log.e(TAG, "Failed to create GPU worker fake surface: " + egl.eglGetError());
                    _gpuWorkerFakeSurface = null;
                }
            }

            MapRendererSetupOptions rendererSetupOptions = new MapRendererSetupOptions();
            if (_gpuWorkerContext != null && _gpuWorkerFakeSurface != null) {
                rendererSetupOptions.setGpuWorkerThreadEnabled(true);
                _gpuWorkerThreadPrologue = new GpuWorkerThreadPrologue(egl, display, _gpuWorkerContext, _gpuWorkerFakeSurface);
                rendererSetupOptions.setGpuWorkerThreadPrologue(_gpuWorkerThreadPrologue.getBinding());
                _gpuWorkerThreadEpilogue = new GpuWorkerThreadEpilogue(egl);
                rendererSetupOptions.setGpuWorkerThreadEpilogue(_gpuWorkerThreadEpilogue.getBinding());
            } else {
                rendererSetupOptions.setGpuWorkerThreadEnabled(false);
            }
            _renderRequestCallback = new RenderRequestCallback();
            rendererSetupOptions.setFrameUpdateRequestCallback(_renderRequestCallback.getBinding());
            _mapRenderer.setup(rendererSetupOptions);

            return mainContext;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);

            if (_gpuWorkerContext != null) {
                egl.eglDestroyContext(display, _gpuWorkerContext);
                _gpuWorkerContext = null;
            }

            if (_gpuWorkerFakeSurface != null) {
                egl.eglDestroySurface(display, _gpuWorkerFakeSurface);
                _gpuWorkerFakeSurface = null;
            }
        }
    }

    private class Renderer implements GLSurfaceView.Renderer {
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.i(TAG, "onSurfaceCreated");
            if (_mapRenderer.isRenderingInitialized())
                _mapRenderer.releaseRendering();
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.i(TAG, "onSurfaceChanged");
            _mapRenderer.setViewport(new AreaI(0, 0, height, width));
            _mapRenderer.setWindowSize(new PointI(width, height));

            if (!_mapRenderer.isRenderingInitialized())
            {
                if (!_mapRenderer.initializeRendering())
                    Log.e(TAG, "Failed to initialize rendering");
            }
        }

        public void onDrawFrame(GL10 gl) {
            _mapRenderer.update();

            if (_mapRenderer.prepareFrame())
                _mapRenderer.renderFrame();

            gl.glFlush();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}
