package de.tum.mw.lfe.mtt;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import de.tum.mw.lfe.mtt.R.color;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemClock;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


//------------------------------------------------------
//Revision History 'Mobile Tracking Task (MTT)'
//------------------------------------------------------
//Version	Date			Author				Mod
//1			Oct, 2014		Michael Krause		initial
//1.1		Aug, 2016		Michael Krause		added live view RMSE
//------------------------------------------------------

/*
Copyright (C) 2014  Michael Krause (krause@tum.de), Institute of Ergonomics, Technische Universität München

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/


public class MainActivity extends Activity implements SensorEventListener{
	private static final String TAG = "MTT.Activity";
	private static final String PREFERENCES = "mttPreferences";
	private PowerManager.WakeLock mWakeLock;	
	
	private AlertDialog mAlert; //screen menue / start dialog
	
	
	private Thread mThread;
	private Instability mInstability;

	
	private SensorManager mSensorManager;
	private Sensor mAcc;
	private float[] mNeutral = new float[3];//accelerometer data for neutral position
	private float[] mLastAcc = new float[3];//last accelerometer data for use by setNeutralPosition() 
	
	//logging
	private File mFile=null;
	public static final String CSV_DELIMITER = ";"; //delimiter within csv
	public static final String CSV_LINE_END = "\r\n"; //line end in csv
	public static final String FOLDER = "MobileTrackingTask"; //folder
	public static final String FOLDER_DATE_STR = "yyyy-MM-dd";//logging folder format
	public static final String FILE_EXT = ".txt";
	public static final String HEADER ="timestamp;x;y;diffRoll;diffPitch;RMSE_X;RMSE_Y;RMSE";
	public static final String HEADER_CONFIG ="timestamp;flatCheckBox;neutral[0];neutral[1];neutral[2];sensitivityProgressBar;sensitivityX;sensitivityY;instabilityProgressBar;instabilityX;instabilityY;twoDimensional;swapXY;invertX;invertY;liveViewEnabled;liveViewRotation;softwareVersion;";
	    
	//stats
	 private long mLastExperimentDuration = 0;
	 private float mLastExperimentRMSE_X = 0;
	 private float mLastExperimentRMSE_Y = 0;
	 private float mLastExperimentRMSE = 0;	
	
	// instability------------------------------------------------
	private class Instability implements Runnable {

		 private static final String TAG = "MTT.InstabilityThread";		
		 private boolean mEndThread = false;
		 private double mX = DISTURB_X;//horizontal internal state
		 private double mY = DISTURB_Y;//vertical internal state
		 private long mLastTimeInstability = System.currentTimeMillis();//last time the instability was calculated
		 private double mLambdaX = 1.0;//horizontal instability
		 private double mLambdaY = 1.0;//vertical instability
		 private boolean mIsExperimentRunning=false;
		 private long mStartOfExperiment = System.currentTimeMillis();//start time
		 private long mCount = 0;//count for RMSE
		 private double mE_X_SUM = 0.0;//error x sum
		 private double mE_Y_SUM = 0.0;//square error y sum
		 private double mE_SUM = 0.0;//square error total
		 private double mSensitivityX = 1.0;//horizontal sensitivity
		 private double mSensitivityY = 1.0;//vertical sensitivity
		 private boolean mTwoDimensions = true;
		 
		 public static final int RANGE = 100;
		 public static final int MAX_X = +RANGE;
		 public static final int MIN_X = -RANGE;
		 public static final int MAX_Y = +RANGE;
		 public static final int MIN_Y = -RANGE;
		 public static final double DISTURB_X = 1;
		 public static final double DISTURB_Y = 1;		 


		 public static final double ROLL_TH = 2.0f;//angle threshold below this threshold user inputs are ignored
		 public static final double PITCH_TH = 2.0f;//angle threshold below this threshold user inputs are ignored
		 public static final double CONTROL_LIMIT = 12;//maximum for control input above and below is capped
		 
		 
		 //getter/setter------------------------
		 public boolean isExperimentRunning(){
			 return mIsExperimentRunning;
		 }
		 
		 public void setX(double x){
			 mX = x;
			 if (mX > MAX_X) mX = MAX_X;
			 if (mX < MIN_X) mX = MIN_X;
			 if (mX == 0) mX = DISTURB_X;

		 }
		 public void setY(double y){
			 mY = y;
			 if (mY > MAX_Y) mY = MAX_Y;
			 if (mY < MIN_Y) mY = MIN_Y;
			 if (mY == 0) mY = DISTURB_Y;
		 }
		 public double getX(){
			 return mX;
		 }
		 public double getY(){
			 if(mTwoDimensions){
				 return mY;
			 }else{
				 return 0;
			 }
			 
		 }
		 public void setSensitivitX(double sX){
			 mSensitivityX = sX;
		 }
		 public void setSensitivitY(double sY){
			 mSensitivityY = sY;
		 }
		 public double getSensitivityX(){
			 return mSensitivityX;
		 }
		 public double getSensitivityY(){
			 return mSensitivityY;
		 }
		 
		 public void setLambda(double l){//stub/helper
			 setLambdaX(l);
			 setLambdaY(l);
		 }		 		 
		 public void setLambdaX(double lx){
			 mLambdaX = lx;
		 }		 
		 public void setLambdaY(double ly){
			 mLambdaY = ly;
		 }
		 public double getLambdaX(){
			 return mLambdaX;
		 }
		 public double getLambdaY(){
			 return mLambdaY;
		 }
		 
		 public void end() {
		  this.mEndThread = true;
		 }
		 
		 public double getRMSE_X(){
			 if (mCount > 0)
				 return (Math.sqrt(mE_X_SUM/mCount));
			 else
				 return 0f;
		 }	
		 
		 public double getRMSE_Y(){
			 if (mCount > 0)
				 return (Math.sqrt(mE_Y_SUM/mCount));
			 else
				 return 0f;
		 }
		 
		 public double getRMSE(){
			 if (mCount > 0)
				 return (Math.sqrt(mE_SUM/mCount));
			 else
				 return 0f;
		 }		 
		 
		 public void controlX(double inputX){
			 if (inputX > CONTROL_LIMIT) inputX =CONTROL_LIMIT;
			 if (inputX < -CONTROL_LIMIT) inputX =-CONTROL_LIMIT;
			 double tempX = getX();
			 tempX += getSensitivityX()*inputX;
			 setX(tempX);
		 }
		 
		 public void controlY(double inputY){
			 
			 if(!mTwoDimensions) return;//if only one dimensional omit Y
			 
			 if (inputY > CONTROL_LIMIT) inputY =CONTROL_LIMIT;
			 if (inputY < -CONTROL_LIMIT) inputY =-CONTROL_LIMIT;
			 double tempY = getY();
			 tempY += getSensitivityY()*inputY;
			 setY(tempY);
		 }		  

		 public void setTwoDimensional(boolean set){
     		if (set){
    			setLambdaY(getLambdaX());
    			mTwoDimensions = true;
     		}else{
    			setLambdaY(0);
    			setY(DISTURB_Y);
    			mTwoDimensions = false;
    		}
		 }
		 
		 public void stopExperiment(){
			 if(mIsExperimentRunning){
				 mIsExperimentRunning = false;
				 //copy stat values to parent
				 long now = System.currentTimeMillis();
				 mLastExperimentDuration = now - mStartOfExperiment;
				 mLastExperimentRMSE_X = (float)getRMSE_X();
				 mLastExperimentRMSE_Y = (float)getRMSE_Y();
				 mLastExperimentRMSE = (float)getRMSE();
			 }
		 }
		 
		 public void startExperiment(){
			 mIsExperimentRunning = true;
			 mStartOfExperiment = System.currentTimeMillis();
			 reset();
		 }
		 
		 public void reset(){
			 setX(DISTURB_X);
			 setY(DISTURB_Y);
			 mE_X_SUM = 0;
			 mE_Y_SUM = 0;
			 mE_SUM = 0;
			 mCount = 0;
		 }
		 
		 public void handleMovementControlInput(double diffRoll, double diffPitch){ 
			 
			 
            ImageView up = (ImageView)findViewById(R.id.gradientTop);
            ImageView down = (ImageView)findViewById(R.id.gradientBottom);
            ImageView left = (ImageView)findViewById(R.id.gradientLeft);
            ImageView right = (ImageView)findViewById(R.id.gradientRight);
            ImageView temp = new ImageView(getBaseContext());

    	    /*
    	    //swap visual indicators
    	    CheckBox swapCheckBox = (CheckBox)findViewById(R.id.swapCheckBox);
    	    CheckBox invertXCheckBox = (CheckBox)findViewById(R.id.invertXCheckBox);
    	    CheckBox invertYCheckBox = (CheckBox)findViewById(R.id.invertYCheckBox);

        	if (swapCheckBox.isChecked()){//swap x and y axis
                up = (ImageView)findViewById(R.id.gradientRight);	
        		right = (ImageView)findViewById(R.id.gradientTop);       		
        		down = (ImageView)findViewById(R.id.gradientLeft);
        		left = (ImageView)findViewById(R.id.gradientBottom);
        	}
        	
        	//swap visual cues according to checkboxes (numerical values are swaped in onSensorChanged())
        	if (invertXCheckBox.isChecked()){//invert x axis
        		temp = up;
        		up = down;
        		down = temp;
        	}     	
        		
        	if (invertYCheckBox.isChecked()){//invert y axis
        		temp = left;
        		left = right;
        		right = temp;
        	} 
        	
        	*/
            
            //calculate gradient at the border to additionally visualize control input
            up.setVisibility(View.INVISIBLE);
            down.setVisibility(View.INVISIBLE);
            left.setVisibility(View.INVISIBLE);
            right.setVisibility(View.INVISIBLE);
	            
             if (diffRoll > ROLL_TH){
         	    controlX(diffRoll);
            	ViewGroup.LayoutParams params = (ViewGroup.LayoutParams)right.getLayoutParams();
            	if (params.width != ViewGroup.LayoutParams.FILL_PARENT){//check if horizontal or vertical
            		params.width = (int)Math.round(10 + diffRoll);
            	}else{
            		params.height = (int)Math.round(10 + diffRoll);	
            	}
            	right.setLayoutParams(params);
            	right.setVisibility(View.VISIBLE);
             }
             if (diffRoll < -ROLL_TH){
      	    	controlX(diffRoll);
            	ViewGroup.LayoutParams params = (ViewGroup.LayoutParams)left.getLayoutParams();
            	if (params.width != ViewGroup.LayoutParams.FILL_PARENT){//check if horizontal or vertical
            		params.width = (int)Math.round(10 - diffRoll);
            	}else{
            		params.height = (int)Math.round(10 - diffRoll);	
            	}
            	left.setLayoutParams(params);
            	left.setVisibility(View.VISIBLE);
             }
             if (diffPitch > PITCH_TH){
             	controlY(diffPitch);
            	ViewGroup.LayoutParams params = (ViewGroup.LayoutParams)down.getLayoutParams();
            	if (params.height != ViewGroup.LayoutParams.FILL_PARENT){//check if horizontal or vertical
            		params.height = (int)Math.round(10 + diffPitch);
            	}else{
            		params.width = (int)Math.round(10 + diffPitch);	
            	}            	
            	down.setLayoutParams(params);                	
            	down.setVisibility(View.VISIBLE);
             }
             if (diffPitch < -PITCH_TH){
             	controlY(diffPitch);
               	ViewGroup.LayoutParams params = (ViewGroup.LayoutParams)up.getLayoutParams();
            	if (params.height != ViewGroup.LayoutParams.FILL_PARENT){//check if horizontal or vertical
            		params.height = (int)Math.round(10 - diffPitch);
            	}else{
            		params.width = (int)Math.round(10 - diffPitch);	
            	}            	

            	up.setLayoutParams(params);                	
             	up.setVisibility(View.VISIBLE);
             }          	
         	        
             calculateInstability();

             moveCross(getX(), getY());



             if(mIsExperimentRunning){
            	 logData((float)diffRoll,(float)diffPitch);
             }
		 }
		 
		 private void calculateInstability(){
			 synchronized(this){
				  long now = System.currentTimeMillis();
				  long diff = now - mLastTimeInstability;			  
				  double dt = (now -mLastTimeInstability)/1000.0;
				  setX(getX() + getX() * mLambdaX*dt);
				  if(mTwoDimensions){//if only one dimensional omit Y
					  setY(getY() + getY() * mLambdaY*dt);
				  }else{
					  setY(DISTURB_Y);
				  }
				  mLastTimeInstability = now;
				  
				  //for RMSEs calculation
				  mCount++;
				  mE_X_SUM += Math.pow(Math.abs(getX()),2) ;
				  mE_Y_SUM += Math.pow(Math.abs(getY()),2) ;
				  mE_SUM +=  Math.pow(Math.sqrt(Math.pow(getX(),2) + Math.pow(getY(),2)),2);
			  }
		 }
		 
		 @Override
		 public void run() {
		  while (!mEndThread) {
			  
			  //instability calculation is carried out on every control input; therefore here disabled
			  //calculateInstability();

			  //sleep
			  try{
				  SystemClock.sleep(10);
				  //Thread.sleep(100);
			  }catch(Exception ex){}
		  }
		 }
		}	
	//------------------------------------------------
	
	
	
	public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAcc, 40000);//uS => 25Hz

        
        getWakeLock();
        

        //init instability
    	mInstability = new Instability();
        mThread = new Thread(mInstability);
        mThread.start();
               
        loadPreferences(); // instability must be instantiated before
        
        if (mAlert != null){
        	mAlert.dismiss();
        }

        
        configureScreenMenu();
        
        showOnScreenMenu();
   
    }
	
	public void handleSensitivityChange(int intSensitivity){
	    SeekBar sensitivitySB = (SeekBar)findViewById(R.id.sensitivityBar);
	    sensitivitySB.setProgress(intSensitivity);
	        
   	    TextView t = (TextView)findViewById(R.id.controlSensitivityTextView);
        t.setText("sensitivity of control: " + String.valueOf(intSensitivity));
        if (mInstability != null){
        	mInstability.setSensitivitX((double)intSensitivity/30d); //0..100 intSensitivity from progressBar is scaled to 0..3.33 sensitivity
        	mInstability.setSensitivitY((double)intSensitivity/30d); //0..100 intSensitivity from progressBar is scaled to 0..3.33 sensitivity
        }	
	}

	public void handleDifficultyChange(int intDifficulty){
    	SeekBar difficultySB = (SeekBar)findViewById(R.id.difficultyBar);
    	difficultySB.setProgress(intDifficulty);
         
   	    TextView t = (TextView)findViewById(R.id.difficultyTextView);
        t.setText("instability: " + String.valueOf(intDifficulty));
        if (mInstability != null){
        	mInstability.setLambdaX((double)intDifficulty/25d); // 0..100 intDifficulty is scaled to 0..4 lambda
        	mInstability.setLambdaY((double)intDifficulty/25d); // 0..100 intDifficulty is scaled to 0..4 lambda
        }	
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    
        //no title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,  WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //full light
        android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, 255); 
		
		setContentView(R.layout.activity_main);
        
		//sensors
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        //init seekbars
   	    SeekBar sensitivitySB = (SeekBar)findViewById(R.id.sensitivityBar);
   	    sensitivitySB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
	
   	    	
	   	    @Override
	   	    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
   	    		if (fromUser == false){
   	    			return;
   	    		}
   	    		
	   	    	int mod = progress % 5; 
	   	        if (mod != 0) progress -= mod;
	   	        
	   	        handleSensitivityChange(progress);
	   	        
	   	    }
	
	   	    @Override
	   	    public void onStartTrackingTouch(SeekBar seekBar) {
	
	   	    }
	
	   	    @Override
	   	    public void onStopTrackingTouch(SeekBar seekBar) {
	
	   	    }
	   	});
   	    
   	    SeekBar difficultySB = (SeekBar)findViewById(R.id.difficultyBar);
   	    difficultySB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   	 	
	   	    @Override
	   	    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
   	    		if (fromUser == false){
   	    			return;
   	    		}	   	    	
	   	    	int mod = progress % 5; 
	   	        if (mod != 0) progress -= mod;
	   	        
	   	     handleDifficultyChange(progress);
	   	    }
	
	   	    @Override
	   	    public void onStartTrackingTouch(SeekBar seekBar) {
	
	   	    }
	
	   	    @Override
	   	    public void onStopTrackingTouch(SeekBar seekBar) {
	
	   	    }
	   	});
   	    
	   	 //two dimension checkbox   
	   	 CheckBox twoDimCheckBox = (CheckBox) findViewById (R.id.twoDimCheckBox);   	    
	   	 twoDimCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
	        @Override
	        public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
        		if (mInstability != null){
        			mInstability.setTwoDimensional(isChecked);
        		}
	        }
	   	 }); 	
	   	 
	   	 
   	    
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}


    public void onPause() {
        super.onPause();

        stopExperiment();
  
        mSensorManager.unregisterListener(this);
        
        saveToPrefs();

        mAlert.dismiss(); 
        
        if(mInstability != null){
        	mInstability.end();
        	mInstability = null;
        }
        if (mThread != null){
        	try{mThread.interrupt();}catch(Exception e){}
        	mThread = null;
        }
       
        
        if(mWakeLock != null){
        	mWakeLock.release();
        	mWakeLock = null;
        }
        
        
        
    }
    
    public void onDestroy(){
    	super.onDestroy();   

 
    }
    
  	
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mAcc) {
        	float[] acc = new float[3]; 
        	acc = event.values.clone();
        	mLastAcc = event.values.clone();//store for use in set neutral position by setNeutralPosition() 
            float[] neutral = new float[3];
            
    	    CheckBox flatPositionCheckBox = (CheckBox)findViewById(R.id.flatPositionCheckBox);
    	    CheckBox swapCheckBox = (CheckBox)findViewById(R.id.swapCheckBox);
    	    CheckBox invertXCheckBox = (CheckBox)findViewById(R.id.invertXCheckBox);
    	    CheckBox invertYCheckBox = (CheckBox)findViewById(R.id.invertYCheckBox);
			CheckBox liveViewCheckBox = (CheckBox)findViewById(R.id.liveViewCheckBox);
			TextView liveViewTextView = (TextView)findViewById(R.id.liveViewTextView);
     

        	if (flatPositionCheckBox.isChecked()){
        		neutral[0] = 0;
        		neutral[1] = 0;
        		neutral[2] = SensorManager.GRAVITY_EARTH;
        	}else{
        		neutral = mNeutral.clone();
        	}
        	
        	if (swapCheckBox.isChecked()){//swap x and y axis
        		float[] acc_temp = new float[3];
        		acc_temp = acc.clone();
        		acc[0] = acc_temp[1];
        		acc[1] = acc_temp[0];
        		float[] acc_neutral = new float[3];
        		acc_neutral = neutral.clone();
        		neutral[0] = acc_neutral[1];
        		neutral[1] = acc_neutral[0];
        	}
        	
        	if (invertXCheckBox.isChecked()){//invert x axis
        		acc[0] *= -1;
        		neutral[0] *= -1;
        	}      	
        		
        	if (invertYCheckBox.isChecked()){//invert y axis
        		acc[1] *= -1;
        		neutral[1] *= -1;
        	}      	
          
            
            double roll = Math.atan2(acc[1], acc[2]) * 180/Math.PI;
            double pitch = Math.atan2(acc[0], Math.sqrt(acc[1]*acc[1] + acc[2]*acc[2])) * 180/Math.PI;
            
 
        	double neutralRoll = Math.atan2(neutral[1], neutral[2]) * 180/Math.PI;
            double neutralPitch = Math.atan2(neutral[0], Math.sqrt(neutral[1]*neutral[1] + neutral[2]*neutral[2])) * 180/Math.PI;
            
    
            if (mInstability != null){
            	            	
            	mInstability.handleMovementControlInput((roll -neutralRoll),(pitch-neutralPitch));


				if (liveViewCheckBox.isChecked()){
					liveViewTextView.setVisibility(View.VISIBLE);

					liveViewTextView.setText(Long.toString(Math.round(mInstability.getRMSE())));//refresh RMSE result

					//rotate textview
					RadioGroup liveViewRotationRadioGroup = (RadioGroup)findViewById(R.id.liveViewRotationRadioGroup);
					float textRotation = 0;
					switch (getSelectedIndexFromRadioGroup(liveViewRotationRadioGroup)) {
						case 0:  textRotation = 0;
							break;
						case 1:  textRotation = 90;
							break;
						case 2:  textRotation = 180;
							break;
						case 3:  textRotation = 270;
							break;
						default: textRotation = 0;
							break;
					}
					if (Build.VERSION.SDK_INT >= 11){
						liveViewTextView.setRotation(textRotation);
					}
				}else{//!(liveViewCheckBox.isChecked())
					liveViewTextView.setVisibility(View.INVISIBLE);
				}
			}

        }
    }
	
    protected void getWakeLock(){
	    try{
			PowerManager powerManger = (PowerManager) getSystemService(Context.POWER_SERVICE);
	        //mWakeLock = powerManger.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.FULL_WAKE_LOCK, "de.tum.ergonomie.mtt");
	        mWakeLock = powerManger.newWakeLock(PowerManager.FULL_WAKE_LOCK, "de.tum.ergonomie.mtt");
            mWakeLock.acquire();
	       //  if (mWakeLock.isHeld() == false){ mWakeLock.acquire();}
		}catch(Exception e){
        	Log.e(TAG,"get wakelock failed:"+ e.getMessage());
		}	
    }	
    
	private String getVersionString(){
		String retString = "";
		String appVersionName = "";
		int appVersionCode = 0;
		try{
			appVersionName = getPackageManager().getPackageInfo(getPackageName(), 0 ).versionName;
			appVersionCode= getPackageManager().getPackageInfo(getPackageName(), 0 ).versionCode;
		}catch (Exception e) {
			Log.e(TAG, "getVersionString failed: "+e.getMessage());
		 }
		
		retString = "V"+appVersionName+"."+appVersionCode;
		
		return retString;
	}	    
 
	private void toasting(final String msg, final int duration){
		Context context = getApplicationContext();
		CharSequence text = msg;
		Toast toast = Toast.makeText(context, text, duration);
		toast.show();		
	}
	
	public File  prepareLogging(){
		File file = null;
		File folder = null;
		SimpleDateFormat  dateFormat = new SimpleDateFormat(FOLDER_DATE_STR);
		String folderTimeStr =  dateFormat.format(new Date());
		String timestamp = Long.toString(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
	   try{
		   //try to prepare external logging
		   String folderStr = Environment.getExternalStorageDirectory () + File.separator + FOLDER + File.separator + folderTimeStr;
		   file = new File(folderStr, timestamp + FILE_EXT);
		   folder = new File(folderStr);
		   folder.mkdirs();//create missing dirs
		   file.createNewFile();
		   if (!file.canWrite()) throw new Exception();
	
	   }catch(Exception e){
		   try{
	    	   error("maybe no SD card inserted");//toast
			   finish();//we quit. we will not continue without file logging

			   //we do not log to internal memory, its not so easy to get the files back, external is easier via usb mass storage
			   /*
			   //try to prepare internal logging
				File intfolder = getApplicationContext().getDir("data", Context.MODE_WORLD_WRITEABLE);
				String folderStr = intfolder.getAbsolutePath() + File.separator + folderTimeStr;
				toasting("logging internal to: " +folderStr, Toast.LENGTH_LONG);
				file = new File(folderStr, timestamp + FILE_EXT);
			    folder = new File(folderStr);
			    folder.mkdirs();//create missing dirs
				file.createNewFile();
				if (!file.canWrite()) throw new Exception();
				*/
		   }catch(Exception e2){
			   file= null;
	    	   error("exception during prepareLogging(): " + e2.getMessage());//toast
			   finish();//we quit. we will not continue without file logging
		   } 
		   
		  		   
		   
	   }
	   return file;
	}	
	
	
	public void logConfig(){
		 long now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis(); 
		//"timestamp;flatCheckBox;neutral[0];neutral[1];neutral[2];sensitivityProgressBar;sensitivityX;sensitivityY;instabilityProgressBar;instabilityX;instabilityY;twoDimensional;swapxy;invertx;inverty;softwareVersion;";
		 if (mInstability == null){error("null in logConfig()");return;}
		
	   	SeekBar sensitivitySB = (SeekBar)findViewById(R.id.sensitivityBar);
   	    SeekBar difficultySB = (SeekBar)findViewById(R.id.difficultyBar);
	    CheckBox flatPositionCheckBox = (CheckBox)findViewById(R.id.flatPositionCheckBox);
   	    CheckBox twoDimCheckBox = (CheckBox) findViewById (R.id.twoDimCheckBox);
	    CheckBox swapCheckBox = (CheckBox)findViewById(R.id.swapCheckBox);
	    CheckBox invertXCheckBox = (CheckBox)findViewById(R.id.invertXCheckBox);
	    CheckBox invertYCheckBox = (CheckBox)findViewById(R.id.invertYCheckBox);
		CheckBox liveViewCheckBox = (CheckBox)findViewById(R.id.liveViewCheckBox);
		RadioGroup liveViewRotationRadioGroup = (RadioGroup)findViewById(R.id.liveViewRotationRadioGroup);


		StringBuilder log = new StringBuilder(2048);
		 log.append(HEADER_CONFIG);
		 log.append(CSV_LINE_END);
		 log.append(now);
		 log.append(CSV_DELIMITER);
		 log.append(flatPositionCheckBox.isChecked());
		 log.append(CSV_DELIMITER);
		 log.append(mNeutral[0]);
		 log.append(CSV_DELIMITER);
		 log.append(mNeutral[1]);
		 log.append(CSV_DELIMITER);
		 log.append(mNeutral[2]);
		 log.append(CSV_DELIMITER);
		 log.append(sensitivitySB.getProgress());
		 log.append(CSV_DELIMITER);
		 log.append(mInstability.getSensitivityX());
		 log.append(CSV_DELIMITER);
		 log.append(mInstability.getSensitivityY());
		 log.append(CSV_DELIMITER);
		 log.append(difficultySB.getProgress());
		 log.append(CSV_DELIMITER);
		 log.append(mInstability.getLambdaX());
		 log.append(CSV_DELIMITER);
		 log.append(mInstability.getLambdaY());
		 log.append(CSV_DELIMITER);
		 log.append(twoDimCheckBox.isChecked());
		 log.append(CSV_DELIMITER);
		 log.append(swapCheckBox.isChecked());
		 log.append(CSV_DELIMITER);
		 log.append(invertXCheckBox.isChecked());
		 log.append(CSV_DELIMITER);
		 log.append(invertYCheckBox.isChecked());
		 log.append(CSV_DELIMITER);
		 log.append(liveViewCheckBox.isChecked());
		 log.append(CSV_DELIMITER);
		 log.append(getSelectedIndexFromRadioGroup(liveViewRotationRadioGroup));
		 log.append(CSV_DELIMITER);
		 log.append(getVersionString());
		 log.append(CSV_DELIMITER);
		 log.append(CSV_LINE_END);
		 writeToLogFile(log);	
	}
	
	public void logDataHeader(){
		 StringBuilder log = new StringBuilder(2048);
		 log.append(HEADER);
		 log.append(CSV_LINE_END);
		 writeToLogFile(log);	
	}
	
	public void logHelper(){
		
	}
	
	//StringBuilder log = new StringBuilder(1024);

	public void logData(float diffRoll, float diffPitch){//log rmse data from mInstability with the last user input (diffRoll,diffPitch)
		 long now = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
		 
		 if (mInstability == null){error("null in logData()");return;}
	
		 //timestamp;x;y;diffRoll;diffPitch;RMSE_X,RMSE_Y;RMSE
		 
		 StringBuilder log = new StringBuilder(1024);
		 log.append(now);
		 log.append(CSV_DELIMITER);
		 
		 log.append(String.format("%.2f",mInstability.getX()));
		 log.append(CSV_DELIMITER);
		 log.append(String.format("%.2f",mInstability.getY()));
		 log.append(CSV_DELIMITER);
		 log.append(String.format("%.2f",diffRoll));
		 log.append(CSV_DELIMITER);
		 log.append(String.format("%.2f",diffPitch));
		 log.append(CSV_DELIMITER);
		 log.append(String.format("%.2f",mInstability.getRMSE_X()));
		 log.append(CSV_DELIMITER);
		 log.append(String.format("%.2f",mInstability.getRMSE_Y()));
		 log.append(CSV_DELIMITER);
		 log.append(String.format("%.2f",mInstability.getRMSE()));
		 log.append(CSV_LINE_END);

		 writeToLogFile(log);
		
	}	

	public void writeToLogFile(StringBuilder log){
		byte[] data=null;
		
		synchronized(mFile){

	   		if (log == null){//error
	       		error("writeFile() log==null?!");
	       		finish();//we quit. we will not continue without file logging
	   		}
	   		
		   try{
			   String tempStr = log.toString();
			   data = tempStr.getBytes("US-ASCII");
		   }catch(Exception e){
			   error("error writing log data: "+e.getMessage());//toast
			   finish();//we quit. we will not continue without file logging
		   }
		   		
			FileOutputStream dest = null; 
								
			try {
				dest = new FileOutputStream(mFile, true);
				dest.write(data);
			}catch(Exception e){
				error("writeFile() failed. msg: " + e.getMessage());
	       		finish();//we quit. we will not continue without file logging
				
			}finally {
				try{
					dest.flush();
					dest.close();
				}catch(Exception e){}
			}
		}//synchronized	
		return;
   }
	
	private void error(final String msg){//toast and log some errors
		toasting(msg, Toast.LENGTH_LONG);
		Log.e(TAG,msg);
	}
	
	private void moveCross(double posx, double posy){//posy and posx are in the range -100...+100
		
      	RelativeLayout layout = (RelativeLayout) findViewById(R.id.RelativeLayout1);  
		ImageView crossImageView = (ImageView)findViewById(R.id.cross);
		ImageView circleImageView = (ImageView)findViewById(R.id.circle);
      	//DisplayMetrics metrics = new DisplayMetrics();
      	//getWindowManager().getDefaultDisplay().getMetrics(metrics);
      	
		
      	float x = (float) ((float)layout.getWidth()/2f + (float)layout.getWidth()/2f * (posx/(float)Instability.RANGE));
      	float y = (float) ((float)layout.getHeight()/2f + (float)layout.getHeight()/2f * (posy/(float)Instability.RANGE));
      	   	
  		crossImageView.setX(x - (float)crossImageView.getWidth()/2f);
  		crossImageView.setY(y - (float)crossImageView.getHeight()/2f);
  		//crossImageView.setTranslationX(leftMargin);
  		//crossImageView.setTranslationY(topMargin);
  		
  		circleImageView.setX((float)layout.getWidth()/2f - (float)circleImageView.getWidth()/2f);
  		circleImageView.setY((float)layout.getHeight()/2f - (float)circleImageView.getHeight()/2f);

 		
  		/*      	      	
      	ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)crossImageView.getLayoutParams();
      	
      	int leftMargin = (int)x - mCrossWidth/2;
      	int topMargin = (int)y - mCrossHeight/2;
  		int rightMargin = layout.getWidth() - leftMargin - mCrossWidth;
      	int bottomMargin = layout.getHeight() - topMargin - mCrossHeight;      	
  		params.setMargins(leftMargin, topMargin, rightMargin, bottomMargin);
  		
      	crossImageView.setLayoutParams(params);
      	crossImageView.requestLayout();
      	*/
      	     	
	}	
	
	@Override
	 public void onWindowFocusChanged(boolean hasFocus) {
	  super.onWindowFocusChanged(hasFocus);
	  initGraphics();
	 }	
	
	private void initGraphics(){
		
		
      	RelativeLayout layout = (RelativeLayout) findViewById(R.id.RelativeLayout1); 
		ImageView circleImageView = (ImageView)findViewById(R.id.circle);
		ImageView crossImageView = (ImageView)findViewById(R.id.cross);
      	layout.bringChildToFront(circleImageView);
      	layout.bringChildToFront(crossImageView);//cross in front of circle
      	     	
      	DisplayMetrics metrics = new DisplayMetrics();
      	getWindowManager().getDefaultDisplay().getMetrics(metrics); 
      	
      	//make the bitmap 10mmx10mm
      	//float pixelsPerMillimeter =  ((float)metrics.densityDpi / 25.4f);          	
      	float pixelsPerMillimeterX =  ((float)metrics.xdpi / 25.4f);          	
      	float pixelsPerMillimeterY =  ((float)metrics.ydpi / 25.4f);
      	//int bitmapSizeX = (int)(10 * pixelsPerMillimeterX);//bad style 10mm=63dp also hardcoded in layout => refactor
      	//int bitmapSizeY = (int)(10 * pixelsPerMillimeterY);//bad style 10mm=63dp also hardcoded in layout => refactor
      	
      	int circleBitmapSizeX = circleImageView.getWidth();
      	int circleBitmapSizeY = circleImageView.getHeight();
      	
      	Bitmap circleBitmap = Bitmap.createBitmap(circleBitmapSizeX, circleBitmapSizeY, Bitmap.Config.ARGB_8888);//heuer bitmap        
      	Canvas circleCanvas = new Canvas(circleBitmap);
      	circleCanvas.setDensity(Bitmap.DENSITY_NONE);
        Paint paint = new Paint();
      	paint.setStyle(Paint.Style.FILL);
      	paint.setFlags(Paint.ANTI_ALIAS_FLAG);
      	paint.setColor(getResources().getColor(R.color.CIRCLE));
      	//float circleRadius = pixelsPerMillimeterX * 2f;
      	float circleRadius =  (int) getResources().getDimension(R.dimen.circle_radius);
      	circleCanvas.drawCircle(circleBitmapSizeX/2, circleBitmapSizeY/2, circleRadius, paint);      	
    	Drawable circleDrawable = new BitmapDrawable(Bitmap.createScaledBitmap(circleBitmap, circleBitmapSizeX, circleBitmapSizeY, true));
    	circleImageView.setImageDrawable(circleDrawable);

      	int crossBitmapSizeX = crossImageView.getWidth();
      	int crossBitmapSizeY = crossImageView.getHeight();
      	
      	Bitmap crossBitmap = Bitmap.createBitmap(crossBitmapSizeX, crossBitmapSizeY, Bitmap.Config.ARGB_8888);//needle bitmap    	
      	Canvas crossCanvas = new Canvas(crossBitmap);
      	crossCanvas.setDensity(Bitmap.DENSITY_NONE);
        paint = new Paint();     	
        
      	paint.setStyle(Paint.Style.FILL);
      	paint.setFlags(Paint.ANTI_ALIAS_FLAG);
      	//paint.setStrokeWidth(pixelsPerMillimeterX * 1.4f);
      	paint.setStrokeWidth((int) getResources().getDimension(R.dimen.cross_stroke_width));
      	paint.setColor(getResources().getColor(R.color.CROSS));
      	crossCanvas.drawLine(crossBitmapSizeX/2, 0, crossBitmapSizeY/2, crossBitmapSizeY, paint );
      	crossCanvas.drawLine(0, crossBitmapSizeX/2, crossBitmapSizeY, crossBitmapSizeY/2, paint );
   	
    	Drawable crossDrawable = new BitmapDrawable(Bitmap.createScaledBitmap(crossBitmap, crossBitmapSizeX, crossBitmapSizeY, true));
    	crossImageView.setImageDrawable(crossDrawable);
    	

	}

	private int getSelectedIndexFromRadioGroup(RadioGroup rg){//helper get index of selected radio button e.g. first radio button is selected => 0
		int radioButtonID = rg.getCheckedRadioButtonId();
		View radioButton = rg.findViewById(radioButtonID);
		int radioButtonIdx = rg.indexOfChild(radioButton);
		return radioButtonIdx;

	}

	private void loadPreferences(){
		//load from preferences
	    SharedPreferences settings = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
	    
	    //flatPositionCheckBox
	    CheckBox flatPositionCheckBox = (CheckBox)findViewById(R.id.flatPositionCheckBox);	    
	    flatPositionCheckBox.setChecked(settings.getBoolean("flatPositionCheckBox", true));
	    
	    
	    //mNeutralPosition;
	    mNeutral[0] = settings.getFloat("mNeutral0", 0f);
	    mNeutral[1] = settings.getFloat("mNeutral1", 0f);
	    mNeutral[2] = settings.getFloat("mNeutral2", 0f);
	    
	    
	    //sensitivityBar
   	    SeekBar sensitivitySB = (SeekBar)findViewById(R.id.sensitivityBar);
   	    handleSensitivityChange(settings.getInt("sensitivitySB", 25));
   	    
   	    //difficultyBar
   	    SeekBar difficultyBar = (SeekBar)findViewById(R.id.difficultyBar);
   	    handleDifficultyChange(settings.getInt("difficultyBar", 25));
   	    
	    //twoDimCheckBox
	    CheckBox twoDimCheckBox = (CheckBox)findViewById(R.id.twoDimCheckBox);	    
	    twoDimCheckBox.setChecked(settings.getBoolean("twoDimCheckBox", true));
	    mInstability.setTwoDimensional(twoDimCheckBox.isChecked());

	    //swapCheckBox
	    CheckBox swapCheckBox = (CheckBox)findViewById(R.id.swapCheckBox);	    
	    swapCheckBox.setChecked(settings.getBoolean("swapCheckBox", false));
	    
	    //invertXCheckBox
	    CheckBox invertXCheckBox = (CheckBox)findViewById(R.id.invertXCheckBox);	    
	    invertXCheckBox.setChecked(settings.getBoolean("invertXCheckBox", false));
	    
	    //invertYCheckBox
	    CheckBox invertYCheckBox = (CheckBox)findViewById(R.id.invertYCheckBox);	    
	    invertYCheckBox.setChecked(settings.getBoolean("invertYCheckBox", false));

		//liveViewCheckBox
		CheckBox liveViewCheckBox = (CheckBox)findViewById(R.id.liveViewCheckBox);
		liveViewCheckBox.setChecked(settings.getBoolean("liveViewCheckBox", false));

		//liveViewRotation
		int radioButtonIdx = settings.getInt("liveViewRotationRadioGroup", 0);
		RadioGroup liveViewRotationRadioGroup = (RadioGroup)findViewById(R.id.liveViewRotationRadioGroup);
		((RadioButton)liveViewRotationRadioGroup.getChildAt(radioButtonIdx)).setChecked(true);




	    //experiment stats
	    mLastExperimentDuration = settings.getLong("mLastExperimentDuration", 0);
	    mLastExperimentRMSE_X = settings.getFloat("mLastExperimentRMSE_X", 0f);
	    mLastExperimentRMSE_Y = settings.getFloat("mLastExperimentRMSE_Y", 0f);
	    mLastExperimentRMSE = settings.getFloat("mLastExperimentRMSE", 0f);
	}
	
	
	private void saveToPrefs(){
		//save changes to app preferences
	    SharedPreferences settings = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
	    SharedPreferences.Editor editor = settings.edit();
	    
	    //flatPositionCheckBox
	    CheckBox flatPositionCheckBox = (CheckBox)findViewById(R.id.flatPositionCheckBox);
	    editor.putBoolean("flatPositionCheckBox", flatPositionCheckBox.isChecked());
	    
	    //mNeutral;
	    editor.putFloat("mNeutral0", mNeutral[0]);
	    editor.putFloat("mNeutral1", mNeutral[1]);
	    editor.putFloat("mNeutral2", mNeutral[2]);
	    
	    //sensitivityBar
   	    SeekBar sensitivitySB = (SeekBar)findViewById(R.id.sensitivityBar);
   	    editor.putInt("sensitivitySB", sensitivitySB.getProgress());
   	    
   	    //difficultyBar
   	    SeekBar difficultyBar = (SeekBar)findViewById(R.id.difficultyBar);
   	    editor.putInt("difficultyBar", difficultyBar.getProgress());
   	    
	    //twoDimCheckBox
	    CheckBox twoDimCheckBox = (CheckBox)findViewById(R.id.twoDimCheckBox);
	    editor.putBoolean("twoDimCheckBox", twoDimCheckBox.isChecked());
	    
	    //swapCheckBox
	    CheckBox swapCheckBox = (CheckBox)findViewById(R.id.swapCheckBox);
	    editor.putBoolean("swapCheckBox", swapCheckBox.isChecked());
	    
	    //invertXCheckBox
	    CheckBox invertXCheckBox = (CheckBox)findViewById(R.id.invertXCheckBox);
	    editor.putBoolean("invertXCheckBox", invertXCheckBox.isChecked());
	    
	    //invertYCheckBox
	    CheckBox invertYCheckBox = (CheckBox)findViewById(R.id.invertYCheckBox);
	    editor.putBoolean("invertYCheckBox", invertYCheckBox.isChecked());

		//liveViewCheckBox
		CheckBox liveViewCheckBox = (CheckBox)findViewById(R.id.liveViewCheckBox);
		editor.putBoolean("liveViewCheckBox", liveViewCheckBox.isChecked());

		//liveViewRotation
		RadioGroup liveViewRotationRadioGroup = (RadioGroup)findViewById(R.id.liveViewRotationRadioGroup);
		int radioButtonIdx = getSelectedIndexFromRadioGroup(liveViewRotationRadioGroup);
		editor.putInt("liveViewRotationRadioGroup", radioButtonIdx);


	    //experiment stats
		 editor.putLong("mLastExperimentDuration", mLastExperimentDuration);
		 editor.putFloat("mLastExperimentRMSE_X", mLastExperimentRMSE_X);
		 editor.putFloat("mLastExperimentRMSE_Y", mLastExperimentRMSE_Y);
		 editor.putFloat("mLastExperimentRMSE", mLastExperimentRMSE);
		 
	    editor.commit();	
	    
	}
	
	private void showConfigure(){
	   	   ScrollView scrollView = (ScrollView)findViewById(R.id.scrollViewConfig);
	   	   scrollView.setVisibility(View.VISIBLE);
	}
	
	private void startExperiment(){
   	   	ScrollView scrollView = (ScrollView)findViewById(R.id.scrollViewConfig);
   	   	scrollView.setVisibility(View.INVISIBLE);
   	   	
   	   	//start logging
		mFile = prepareLogging();
		logConfig();
		logDataHeader();
		
		if (mInstability != null){
			mInstability.startExperiment();
		}else{
			error("error startExperiment()");
		}
	}
	
	private void stopExperiment(){
		if (mInstability != null){
			mInstability.stopExperiment();
		}else{
			error("error stopExperiment()");			
		}
	}
	
	public void experimentStopAndExit(){
		stopExperiment();
		finish();
	}
	
    
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		//event.getRepeatCount()
		
		View foo = new View(getApplicationContext());//foo dummy view
    	
	    switch (keyCode) {//control  e.g. by bluetooth keyboard
	    
        	case KeyEvent.KEYCODE_BACK:
        		ScrollView scrollViewConfig = (ScrollView)findViewById(R.id.scrollViewConfig);
        		if (scrollViewConfig.isShown()) showOnScreenMenu();//if we are in config return to onScreenMenu
        		return true;
        		
        	//controll via bluetooth keyboard	
	        case KeyEvent.KEYCODE_DPAD_UP:
	            return true;
	        case KeyEvent.KEYCODE_DPAD_DOWN:
	            return true;
	        case KeyEvent.KEYCODE_DPAD_LEFT:
	            return true;
	        case KeyEvent.KEYCODE_DPAD_RIGHT:
	            return true;
	        default:
	        	return true;//prevent that keys like back are delivered 
	            //return super.onKeyUp(keyCode, event);
	    }
	}	
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		
		
    	return true;//prevent that keys like back are delivered
        //return super.onKeyUp(keyCode, event);
	}	
	
	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		
    	return true;//prevent that keys like back are delivered
        //return super.onKeyUp(keyCode, event);
	}	
	
	@Override
	public boolean onKeyMultiple (int keyCode,  int count, KeyEvent event) {
		
    	return true;//prevent that keys like back are delivered
        //return super.onKeyUp(keyCode, event);
	}	
		
	
	public void configureScreenMenu(){
		
	      String tempStr = "To stop the experiment use the screen off or home button.";	      
	      
	      tempStr += "<br/>Last result:";
	      tempStr += "<br/>&nbsp;&nbsp;&nbsp;Duration[s]: " + String.format("%.2f",mLastExperimentDuration/1000f);
	      tempStr += "<br/>&nbsp;&nbsp;&nbsp;RMSE_X "+ String.format("%.2f",mLastExperimentRMSE_X);
	      tempStr += "<br/>&nbsp;&nbsp;&nbsp;RMSE_Y "+ String.format("%.2f",mLastExperimentRMSE_Y);
	      tempStr += "<br/>&nbsp;&nbsp;&nbsp;RMSE "+ String.format("%.2f",mLastExperimentRMSE);
	      
	      tempStr += "<br/><br/>This is an open source GPL implementation of a tracking task, called Mobile Tracking Task (MTT)";
	      tempStr += "<br/><br/>(c) Michael Krause <a href=\"mailto:krause@tum.de\">krause@tum.de</a> <br/>2014 Institute of Ergonomics, TUM";
	      tempStr += "<br/><br/>More information on <br/><a href=\"http://www.lfe.mw.tum.de/en/open-source/mtt\">http://www.lfe.mw.tum.de/en/open-source/mtt</a>";
          tempStr += "<br/> This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.";


	      final SpannableString s = new SpannableString(Html.fromHtml(tempStr));
	      Linkify.addLinks(s, Linkify.EMAIL_ADDRESSES|Linkify.WEB_URLS);
	      
	      mAlert = new AlertDialog.Builder(this)
	          .setMessage( s )
		      .setTitle("Mobile Tracking Task "+getVersionString())
		      .setPositiveButton("Start",
		         new DialogInterface.OnClickListener() {
			         public void onClick(DialogInterface dialog, int whichButton){
			        	 startExperiment();
			         }
		         })
		      .setNeutralButton("Configure",
		         new DialogInterface.OnClickListener() {
			         public void onClick(DialogInterface dialog, int whichButton){
			        	 showConfigure();		        	 
			         }
		         })
		       //exit app, back button is interrupted by onKey handling intentionally so cant disturb an experiment
		      .setNegativeButton("Exit App",
		         new DialogInterface.OnClickListener() {
			         public void onClick(DialogInterface dialog, int whichButton){
			        	 finish();
			         }
		         })	
		      .setOnCancelListener(new DialogInterface.OnCancelListener() {         
		    	@Override
		    		public void onCancel(DialogInterface dialog) {
		    			finish();
		    		}
		      	})
		      .create();
		   
	}
	
	public void showOnScreenMenu(){

		if (mAlert != null) mAlert.show();
		((TextView)mAlert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance()); 
			
	}
	
	public void setNeutralPosition(View v){//press on 'set neutral position button' calls this
		mNeutral = mLastAcc;
	    CheckBox flatPositionCheckBox = (CheckBox)findViewById(R.id.flatPositionCheckBox);
	    flatPositionCheckBox.setChecked(false);//disable checkbox
	}
	
	
}
