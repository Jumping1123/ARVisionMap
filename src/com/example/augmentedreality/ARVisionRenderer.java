package com.example.augmentedreality;

import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_TEST;
import static android.opengl.GLES20.GL_ONE;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.glBlendFunc;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glViewport;
import static android.opengl.Matrix.multiplyMM;
import static android.opengl.Matrix.rotateM;
import static android.opengl.Matrix.setIdentityM;
import static android.opengl.Matrix.setLookAtM;
import static android.opengl.Matrix.translateM;

import java.util.Scanner;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLSurfaceView.Renderer;
import android.util.Log;

import com.example.objects.Arrow;
import com.example.objects.BannerNon;
import com.example.objects.RawObject;
import com.example.objects.RawVertexObject;
import com.example.objects.Square;
import com.example.openglbasics.R;
import com.example.util.ColorShaderProgram;
import com.example.util.MatrixHelper;
import com.example.util.ObjectShaderProgram;
import com.example.util.TextResourceReader;
import com.example.util.TextureHelper;
import com.example.util.TextureShaderProgram;
import com.example.util.TransparentTextureShaderProgram;
import com.example.util.VertexObjectShaderProgram;

public class ARVisionRenderer implements Renderer {

	private final Context context;

	// ---------------------- Matrices used for transformations -----------------------
	// general project matrix and view matrix
	private float[] sensorProjectionMatrix;
	private float[] sensorViewMatrix;

	// model, view, final matrices for each object
	private float[][] modelMatrix;
	private float[][] modelViewMatrix;
	private float[][] finalMatrix;
	private float[][] rotationMatrix;
	private float[][] translationMatrix;

	// separate matrices for compass (compass never moves out of screen)
	private float[] compassModelMatrix;
	private float[] compassRotationMatrix;
	private float[] compassTranslationMatrix;
	private float[] compassViewMatrix;
	private float[] compassModelViewMatrix;
	private float[] compassFinalMatrix;

	// separate matrices for exhibits (compass never moves out of screen)
	private float[] navigationModelMatrix;
	private float[] navigationRotationMatrix;
	private float[] navigationTranslationMatrix;
	private float[] navigationViewMatrix;
	private float[] navigationModelViewMatrix;
	private float[] navigationFinalMatrix;
	
	// separate matrices for exhibits (compass never moves out of screen)
	private float[][] exhibitModelMatrix;
	private float[][] exhibitRotationMatrix;
	private float[][] exhibitTranslationMatrix;
	private float[][] exhibitViewMatrix;
	private float[][] exhibitModelViewMatrix;
	private float[][] exhibitFinalMatrix;

	// helper matrices for setting up camera lookat
	float[] forward;
	float[] up_portrait;
	float[] up_landscape;
	//---------------------------------------------------------------------------------
	// ---------------------------- Location parameters -------------------------------
	// projection matrix parameters
	final static float far = 20f;
	final static float near = 0.2f;

	// distance on the z axis
	//private final float[] zdis = new float[MAXNUM_TEXTURES];
	
	private int [] objectType;
	private int [] objectTexture;
	static float [][] objLoc;
	private float [][] objRot;

	private final float zcompass = 1f;
	private final float znavigation = 1.2f;
	private final float [] zexhibit = {1.2f, 1.8f, 2f};

	// other parameters
	private float x = 0;
	private float y = 0;
	private float z = 0;
	//---------------------------------------------------------------------------------
	//------------------------------- Status variables --------------------------------
	private enum Mode {
		MoveObject, MoveCamera;
	}

	private Mode mode = Mode.MoveCamera;
	
	public enum GraphicsStatus {
		Loading, Ready;
	}
	
	public static GraphicsStatus GLStatus = GraphicsStatus.Loading;
	//---------------------------------------------------------------------------------	
	//------------------------------- Objects and textures ----------------------------
	// number of captions (w/ texture)
	private static int numCaptions;
	// number of raw objects (w/ texture)
	private static int numRawObjects;
	// number of arrows (w/o texture)
	private static int numArrows;
	
	// number of objects (numCaptions + numRawObjects + numArrows)
	private int numObjects;
	// number of textures (numCaptions + numRawObjects)
	private int numTextures;

	// objects with textures
	private int [] textures;
	private BannerNon[] caption;
	private int [] captionTexture = {R.drawable.text1_non, R.drawable.text2_non, R.drawable.text3_non,
			R.drawable.text4_non, R.drawable.text5_non};
	
	private RawObject[] object;
	private int [] rawObjTextureMap = {R.drawable.surfobj, R.drawable.tableobj, R.drawable.chairobj};
	private int [] rawObjTexture = {R.drawable.surf, R.drawable.table, R.drawable.chair};
	private float [][] rawObjPallette = {{0.2f,0.4f,1f,0.8f}, {0f,0.5f,0f,0.8f}, {1f,0.84f,0f,0.6f}};
	// compass object
	private Square compass;
	private int compassTexture;
	
	private Square navigation;
	private int navigationTexture;
	
	private RawVertexObject [] vertexObject;
	private int [] rawVertexObjFile = {R.drawable.trexobj, R.drawable.teapot, R.drawable.cal};
	public static int numRawObj = 3;

	// object without texture
	private Arrow[] arrow;
	private float [][] arrowPallette= {{0f, 0.40f, 0.20f}, {0.4f, 0f, 0f}, {0f, 0.2f, 0.4f}};
	private int [] arrowColor;
	int [] arrowStatus;
	
	//---------------------------------------------------------------------------------
	//------------------------------- Shader programs ---------------------------------
	private TextureShaderProgram textureProgram;
	private TransparentTextureShaderProgram transparentProgram;
	private ColorShaderProgram colorProgram;
	private ObjectShaderProgram objectProgram;
	private VertexObjectShaderProgram vertexObjectProgram;
	//---------------------------------------------------------------------------------
	
	public ARVisionRenderer(Context context) {
		this.context = context;
		String mapInput = TextResourceReader.readTextFileFromResource(context, R.raw.map);
		Scanner scan = new Scanner(mapInput);
		
		// reading parameters from input map
		numCaptions = TextResourceReader.readNextInt(scan);
		numRawObjects = TextResourceReader.readNextInt(scan);
		numArrows = TextResourceReader.readNextInt(scan);
		numObjects = numCaptions + numRawObjects + numArrows;
		numTextures = numCaptions + numRawObjects;
		
		// initialize transformation matrices
		initMatrices();
		
		objectType = new int [numObjects];
		objectTexture = new int [numObjects];
		objLoc = new float [numObjects][3];
		objRot = new float [numObjects][3];
		arrowColor = new int [numArrows];
		arrowStatus = new int [numArrows];
		
		for (int i = 0; i < numObjects; i++) {
			objectType[i] = TextResourceReader.readNextInt(scan);
			// caption or raw object
			if (objectType[i] != 2) {
				objectTexture[i] = TextResourceReader.readNextInt(scan);
			}
			else {
				arrowColor[i-numTextures] = TextResourceReader.readNextInt(scan);
				arrowStatus[i-numTextures] = 0;
			}
			for (int j = 0; j < 3; j++)
				objLoc[i][j] = TextResourceReader.readNextFloat(scan);
			for (int j = 0; j < 3; j++)
				objRot[i][j] = TextResourceReader.readNextFloat(scan);
		}
	}

	// initialize transformation matrices
	private void initMatrices() {
		// --------------------------- matrices init -------------------------------
		// general project matrix and view matrix
		sensorProjectionMatrix = new float[16];
		sensorViewMatrix = new float[16];

		// model, view, final matrices for each object
		modelMatrix = new float[numObjects][16];
		modelViewMatrix = new float[numObjects][16];
		finalMatrix = new float[numObjects][16];
		rotationMatrix = new float[numObjects][16];
		translationMatrix = new float[numObjects][16];

		// separate matrices for compass (compass never moves out of screen)
		compassModelMatrix = new float[16];
		compassRotationMatrix = new float[16];
		compassTranslationMatrix = new float[16];
		compassViewMatrix = new float[16];
		compassModelViewMatrix = new float[16];
		compassFinalMatrix = new float[16];
		
		// separate matrices for compass (compass never moves out of screen)
		navigationModelMatrix = new float[16];
		navigationRotationMatrix = new float[16];
		navigationTranslationMatrix = new float[16];
		navigationViewMatrix = new float[16];
		navigationModelViewMatrix = new float[16];
		navigationFinalMatrix = new float[16];
		
		// separate matrices for exhibit (compass never moves out of screen)
		exhibitModelMatrix = new float[numRawObj][16];
		exhibitRotationMatrix = new float[numRawObj][16];
		exhibitTranslationMatrix = new float[numRawObj][16];
		exhibitViewMatrix = new float[numRawObj][16];
		exhibitModelViewMatrix = new float[numRawObj][16];
		exhibitFinalMatrix = new float[numRawObj][16];

		// helper matrices for setting up camera lookat
		forward = new float[3];
		up_portrait = new float[3];
		up_landscape = new float[3];
	}
	
	// initialize objects including captions, raw objects and arrows
	private void initObjects () {
		// initialize captions
		caption = new BannerNon[numCaptions];
		int i = 0;
		for (i = 0; i < numCaptions; i++) {
			caption[i] = new BannerNon();
		}

		// initialize raw objects
		object = new RawObject[numRawObjects];
		for (i = 0; i < numRawObjects; i++) {
			object[i] = new RawObject(context, rawObjTextureMap[objectTexture[i+numCaptions]]);
		}
		vertexObject = new RawVertexObject[numRawObj];
		for (i = 0; i < 3; i++) {
			vertexObject[i] = new RawVertexObject(context, rawVertexObjFile[i]);
		}
		// initialize arrows
		arrow = new Arrow[numArrows];
		for (i = 0; i < numArrows; i++) {
			arrow[i] = new Arrow(0.7f, 1.4f, 0.35f, 0.7f, 32);
		}
		
		// initialize compass
		compass = new Square();
		
		// initializing navigation
		navigation = new Square();
		
		// bind textures
		textures = new int[numTextures];
	}
	
	
	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
		// set black background
		glClearColor(0f, 0f, 0f, 0f);

		// setting up programs
		textureProgram = new TextureShaderProgram(context);
		transparentProgram = new TransparentTextureShaderProgram(context);
		colorProgram = new ColorShaderProgram(context);
		objectProgram = new ObjectShaderProgram(context);
		vertexObjectProgram = new VertexObjectShaderProgram(context);
		
		// initialize objects
		initObjects();
		
		// bind object texture
		int i = 0;
		for (; i < numCaptions; i++)
			textures[i] = TextureHelper.loadTexture(context, captionTexture[objectTexture[i]]);
		for (; i < numTextures; i++) 
			textures[i] = TextureHelper.loadTexture(context, rawObjTexture[objectTexture[i]]);

		// bind compass texture
		compassTexture = TextureHelper.loadTexture(context, R.drawable.compass);
		
		// bind navigation texture
		navigationTexture = TextureHelper.loadTexture(context, R.drawable.arrow_up);
		
		// objects successfully loaded
		GLStatus = GraphicsStatus.Ready;
		
	}

	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height) {
		glViewport(0, 0, width, height);
		MatrixHelper.perspectiveM(sensorProjectionMatrix, 45, (float) width
				/ (float) height, near, far);
		setLookAtM(sensorViewMatrix, 0, 0, 0, 0, 0, 0, -1, 0, 1, 0);
		setLookAtM(compassViewMatrix, 0, 0, 0, 0, 0, 0, -1, 0, 1, 0);
		int i = 0;
		for (; i < numObjects; i++) {
			setIdentityM(modelMatrix[i], 0);
			setIdentityM(translationMatrix[i], 0);
			setIdentityM(rotationMatrix[i], 0);
		}
		setIdentityM(compassRotationMatrix, 0);
		setIdentityM(compassTranslationMatrix, 0);
		translateM(compassTranslationMatrix, 0, 0f, 0f, -zcompass);
		
		setIdentityM(navigationRotationMatrix, 0);
		setIdentityM(navigationTranslationMatrix, 0);
		translateM(navigationTranslationMatrix, 0, 0f, 0f, -znavigation);
		
		for (i = 0; i < numRawObj; i++) {
			setIdentityM(exhibitRotationMatrix[i], 0);
			setIdentityM(exhibitTranslationMatrix[i], 0);
			translateM(exhibitTranslationMatrix[i], 0, 0f, 0f, -zexhibit[i]);
		}
		
		for (i = 0; i < numTextures; i++) {
			translateM(translationMatrix[i], 0, objLoc[i][0], objLoc[i][1], objLoc[i][2]);
			rotateM(rotationMatrix[i], 0, objRot[i][0], 1, 0, 0);
			rotateM(rotationMatrix[i], 0, objRot[i][1], 0, 1, 0);
			rotateM(rotationMatrix[i], 0, objRot[i][2], 0, 0, 1);
			multiplyMVP(rotationMatrix[i], translationMatrix[i],  modelMatrix[i], 
					sensorViewMatrix, modelViewMatrix[i],
					sensorProjectionMatrix, finalMatrix[i]);
		}

		for (; i < numObjects; i++) {
			translateM(translationMatrix[i], 0, objLoc[i][0], objLoc[i][1], objLoc[i][2]);
			rotateM(rotationMatrix[i], 0, objRot[i][0], 1, 0, 0);
			multiplyMVP(rotationMatrix[i], translationMatrix[i], modelMatrix[i], 
					sensorViewMatrix, modelViewMatrix[i],
					sensorProjectionMatrix, finalMatrix[i]);
		}

		multiplyMVP(compassRotationMatrix, compassTranslationMatrix, compassModelMatrix,
				compassViewMatrix, compassModelViewMatrix,
				sensorProjectionMatrix, compassFinalMatrix);
		
		multiplyMVP(navigationRotationMatrix, navigationTranslationMatrix, navigationModelMatrix,
				navigationViewMatrix, navigationModelViewMatrix,
				sensorProjectionMatrix, navigationFinalMatrix);
		
		for (i = 0 ; i< numRawObj; i++) {
			multiplyMVP(exhibitRotationMatrix[i], exhibitTranslationMatrix[i], exhibitModelMatrix[i],
					exhibitViewMatrix[i], exhibitModelViewMatrix[i],
					sensorProjectionMatrix, exhibitFinalMatrix[i]);
		}
	}

	@Override
	public void onDrawFrame(GL10 glUnused) {
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glEnable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		if (ARVIsionActivity.currentMode != ARVIsionActivity.modes.modeVisitor.ordinal() 
				&& ARVIsionActivity.currentMode != ARVIsionActivity.modes.modeApprentice.ordinal()) {
			int i = numCaptions;
	
			objectProgram.useProgram();
			for (; i < numTextures; i++) {
				objectProgram.setUniforms(finalMatrix[i], textures[i]);
				object[i-numCaptions].bindData(objectProgram);
				object[i-numCaptions].draw();
			}
			
			if (ARVIsionActivity.currentMode == ARVIsionActivity.modes.modeCalendar.ordinal()) {
				int selectedObjs[] = {0, 0, 0};
				if (ARVIsionActivity.available) {					
					selectedObjs[0] = 1;
				}
				if (ARVIsionActivity.occupied) {					
					selectedObjs[1] = 1;
				}
				if (ARVIsionActivity.scheduled) {					
					selectedObjs[2] = 1;
				}
			
				colorProgram.useProgram();
				for (i = 0; i < numArrows; i++) {
					if (selectedObjs[arrowColor[i]] == 1) {
						colorProgram.setUniforms(finalMatrix[i+numTextures], arrowPallette[arrowColor[i]][0], 
								arrowPallette[arrowColor[i]][1], 
								arrowPallette[arrowColor[i]][2], 0.6f);
						arrow[i].bindData(colorProgram);
						arrow[i].draw();
					}
				}
			}
			
			i = 0;
			textureProgram.useProgram();
			for (; i < numCaptions; i++) {
				textureProgram.setUniforms(finalMatrix[i], textures[i]);
				caption[i].bindData(textureProgram);
				caption[i].draw();
			}
			
			transparentProgram.useProgram();
			if (ARVIsionActivity.currentMode == ARVIsionActivity.modes.modeNavigation.ordinal()) {
				transparentProgram.setUniforms(navigationFinalMatrix, navigationTexture);
				navigation.bindData(transparentProgram);
				navigation.draw();
			}
			else {
				transparentProgram.setUniforms(compassFinalMatrix, compassTexture);
				compass.bindData(transparentProgram);
				compass.draw();
			}
		}
		
		// visitor mode - Afinia
		if (ARVIsionActivity.currentMode == ARVIsionActivity.modes.modeVisitor.ordinal()
				&& ARVIsionActivity.currentDevice == ARVIsionActivity.devices.Afinia.ordinal()){
			{ 
				int order = ARVIsionActivity.currentPrototypeAfinia;
				vertexObjectProgram.useProgram();
				vertexObjectProgram.setUniforms(exhibitFinalMatrix[order], rawObjPallette[order]);
				vertexObject[order].bindData(vertexObjectProgram);
				vertexObject[order].draw();
			}
		}
	}

	public void sensorUpdate(float mAzimuth, float roll, float pitch,
			double dx, double dy, double dz) {
		if (mode == Mode.MoveObject) {
			for (int i = 0; i < numObjects; i++) {
				rotateM(rotationMatrix[i], 0, mAzimuth, 0f, 1f, 0f);
				rotateM(rotationMatrix[i], 0, pitch, 1f, 0f, 0f);
			}
		} else if (mode == Mode.MoveCamera) {
			x = (float) Math.cos(Math.toRadians(mAzimuth));
			z = (float) Math.sin(Math.toRadians(mAzimuth));
			y = (float) Math.tan(Math.toRadians(-pitch));
			forward[0] = x;
			forward[1] = z;
			forward[2] = y;
			normalize(forward);
			float a = (float) Math.cos(Math.toRadians(-roll - 90));
			float b = (float) Math.sin(Math.toRadians(-roll - 90));
			if (-pitch > 0.1) {
				up_portrait[0] = -x;
				up_portrait[1] = 1.0f / y;
				up_portrait[2] = -z * a;
			} else if (-pitch < 0.1) {
				up_portrait[0] = x;
				up_portrait[1] = -1.0f / y;
				up_portrait[2] = z * a;
			} else {
				up_portrait[0] = 0;
				up_portrait[1] = 1.0f;
				up_portrait[2] = 0;
			}
			normalize(up_portrait);
			up_landscape[0] = z;
			up_landscape[1] = 0;
			up_landscape[2] = -x;
			normalize(up_landscape);

			setLookAtM(sensorViewMatrix, 0, (float) dx , (float) 0, (float) dz ,
					(float) (dx + x), (float) (dy + y),
					(float) (dz + z), a * up_portrait[0] + b
							* up_landscape[0], a * up_portrait[1] + b
							* up_landscape[1], a * up_portrait[2] + b
							* up_landscape[2]);

			if (-pitch > 0.1) {
				up_portrait[0] = 0;
				up_portrait[1] = 1.0f / y;
				up_portrait[2] = a;
			} else if (-pitch < 0.1) {
				up_portrait[0] = 0;
				up_portrait[1] = -1.0f / y;
				up_portrait[2] = -a;
			} else {
				up_portrait[0] = 0;
				up_portrait[1] = 1.0f;
				up_portrait[2] = 0;
			}
			normalize(up_portrait);
			setLookAtM(compassViewMatrix, 0, 0f, 0f, 0f, 0f, y+0.1f, -1f, a
					* up_portrait[0] - b, a * up_portrait[1], a
					* up_portrait[2]);
			setLookAtM(navigationViewMatrix, 0, 0f, 0f, 0f, 0f, y+0.3f, -1f, a
					* up_portrait[0] - b, a * up_portrait[1], a
					* up_portrait[2]);
			setLookAtM(exhibitViewMatrix[ARVIsionActivity.currentPrototypeAfinia], 0, 0f, 0f, 0f, 0f, 0, -1f, - b, a, 0);

		}
		
		int i = 0;
		for (; i < numCaptions; i++) {
			setIdentityM(rotationMatrix[i], 0);
			float navRot = computeRotation(dx, dy, dz, objLoc[i]);
			rotateM(rotationMatrix[i], 0, objRot[i][0], 1, 0, 0);
			rotateM(rotationMatrix[i], 0, objRot[i][1], 0, 1, 0);
			rotateM(rotationMatrix[i], 0, objRot[i][2]-navRot, 0, 0, 1);
			multiplyMVP(rotationMatrix[i], translationMatrix[i],  modelMatrix[i], 
					sensorViewMatrix, modelViewMatrix[i],
					sensorProjectionMatrix, finalMatrix[i]);
		}
		
		
		// update all map objects
		for (; i < numObjects; i++) {
			multiplyVP(modelMatrix[i], sensorViewMatrix, modelViewMatrix[i],
					sensorProjectionMatrix, finalMatrix[i]);
		}

		// update compass orientation during sensor update
		setIdentityM(compassRotationMatrix, 0);
		rotateM(compassRotationMatrix, 0, 10, 1f, 0f, 0f);
		rotateM(compassRotationMatrix, 0, mAzimuth, 0f, 1f, 0f);
		multiplyMVP(compassRotationMatrix, compassTranslationMatrix, compassModelMatrix,
				compassViewMatrix, compassModelViewMatrix,
				sensorProjectionMatrix, compassFinalMatrix);
	
		// update navigation during sensor update
		float navRot = computeRotation(dx, dy, dz, objLoc[numCaptions + numRawObjects+ ARVIsionActivity.currentDevice]);
		setIdentityM(navigationRotationMatrix, 0);
		rotateM(navigationRotationMatrix, 0, 20, 1f, 0f, 0f);
		rotateM(navigationRotationMatrix, 0, navRot+ mAzimuth, 0f, 1f, 0f);
		multiplyMVP(navigationRotationMatrix, navigationTranslationMatrix, navigationModelMatrix,
				navigationViewMatrix, navigationModelViewMatrix,
				sensorProjectionMatrix, navigationFinalMatrix);
		
		// update exhibit posture during sensor update
		int order = ARVIsionActivity.currentPrototypeAfinia;
		setIdentityM(exhibitRotationMatrix[order], 0);
		rotateM(exhibitRotationMatrix[order], 0, ARVIsionActivity.rotateZ[order], 0f, 1f, 0f);
		rotateM(exhibitRotationMatrix[order], 0, ARVIsionActivity.rotateX[order], 1f, 0f, 0f);
		setIdentityM(exhibitTranslationMatrix[order], 0);
		translateM(exhibitTranslationMatrix[order], 0, 0f, 0f, -ARVIsionActivity.zoom[order]*zexhibit[order]/2);
		multiplyMVP(exhibitRotationMatrix[order], exhibitTranslationMatrix[order], exhibitModelMatrix[order],
				exhibitViewMatrix[order], exhibitModelViewMatrix[order],
				sensorProjectionMatrix, exhibitFinalMatrix[order]);
	}
	
	// ---------------------- Helper functions -----------------------
	
	static float computeRotation(double dx, double dy, double dz, float [] dest) {
		float x = dest[0] - (float) dx;
		float z = dest[2] - (float) dz;
		return (float) (Math.atan2(-z, x)/Math.PI*180);
	}
	
	static int getNearestDevice(float dx, float dy, float dz, float mAzimuth) {
		// setting for Nexus 5 (phone)
		float minDiff = 15;
		// setting for Nexus 7 (tablet)
		//float minDiff = 15;
		Log.d("DEBUG", "onLongPress: " + -computeRotation(dx, dy, dz, objLoc[numCaptions + numRawObjects + 0]));
		for (int i = 0; i < numArrows; i++) {
			if (Math.abs((-computeRotation(dx, dy, dz, objLoc[numCaptions + numRawObjects + i]) - mAzimuth)) % 360
					< minDiff 
					|| 
					360 - Math.abs((-computeRotation(dx, dy, dz, objLoc[numCaptions + numRawObjects + i]) - mAzimuth)) % 360
					< minDiff
					) 
				return i;
		}
		return -1;
	}
	
	static float calNearestDevice(float dx, float dy, float dz, float mAzimuth, int p) {
		return computeRotation(dx, dy, dz, objLoc[numCaptions + numRawObjects + p]);
	}
	
	
	// helper: vector normalization
	void normalize(float[] a) {
		double sum = 0;
		for (int i = 0; i < a.length; i++) {
			sum += a[i] * a[i];
		}
		sum = Math.sqrt(sum);
		for (int i = 0; i < a.length; i++) {
			a[i] = (float) (a[i] / sum);
		}

	}

	// helper: calculate MVP from model, view, project
	private void multiplyVP(float [] model, float [] view, float [] modelView, 
			float [] project, float [] modelViewProject) {
		multiplyMM(modelView, 0, view, 0, model, 0);
		multiplyMM(modelViewProject, 0, project, 0, modelView, 0);
	}
	
	// helper: calculate MVP from rotate / translate, model, view, project
	private void multiplyMVP(float [] rt1, float [] rt2, float [] model, 
			float [] view, float [] modelView, 
			float [] project, float [] modelViewProject) {
		multiplyMM(model, 0, rt2, 0, rt1, 0);
		multiplyMM(modelView, 0, view, 0, model, 0);
		multiplyMM(modelViewProject, 0, project, 0, modelView, 0);
	}

}
