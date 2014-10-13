package com.andrewbfang.flickrtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.gmail.yuyang226.flickrj.sample.android.FlickrHelper;
import com.googlecode.flickrjandroid.Flickr;
import com.googlecode.flickrjandroid.FlickrException;
import com.googlecode.flickrjandroid.REST;
import com.googlecode.flickrjandroid.photos.Photo;
import com.googlecode.flickrjandroid.photos.PhotoList;
import com.googlecode.flickrjandroid.photos.PhotosInterface;
import com.googlecode.flickrjandroid.photos.SearchParameters;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Random;

import com.gmail.yuyang226.flickrj.sample.android.FlickrjActivity;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.Constants;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.Card;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.ListCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.NotificationTextCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.SimpleTextCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManager;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteDeckOfCards;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteDeckOfCardsException;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteResourceStore;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteToqNotification;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.resource.CardImage;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.resource.DeckOfCardsLauncherIcon;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.util.ParcelableUtil;

import javax.xml.parsers.ParserConfigurationException;

/**
 * http://taapps-javasamples.blogspot.com/2008/01/javaflickrjflickr-java-apiflickrj.html
 */
public class MyActivity extends Activity {

    //Butons
    private Button submitBut;
    private Button saveBut;
    private Button resetBut;
    private ToggleButton erase;
    private Button pickBut;
    //Paint
    private ImageView iv_canvas;
    private Canvas canvas;
    private Paint paint;
    private Bitmap baseBitmap;

    private Bitmap colorBitmap;
    //Brushes
    private SeekBar colorBar;
    private SeekBar widthBar;
    private int colorProgress;
    private int widthProgress;
    //Uploads
    private File lastPhotoFile;
    public static Bitmap resultImage;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.action_toq_management){
            Intent toToqIntent = new Intent();
            toToqIntent.setClass(MyActivity.this, ToqActivity.class);
            startActivity(toToqIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        Log.e("myact", "myact.oncreat");
//        Log.e("myact", "result image = " + Boolean.toString(resultImage== null));

        //Initialize UI and the listeners
        initUI();
        submitBut.setOnClickListener(mUploadClickListener);
        pickBut.setOnClickListener(mPickClickListener);
        saveBut.setOnClickListener(click);
        resetBut.setOnClickListener(click);
        iv_canvas.setOnTouchListener(touch);
        erase.setOnClickListener(click);
        }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 102) {

            if (resultCode == Activity.RESULT_OK) {
                Uri tmp_fileUri = data.getData();

                iv_canvas.setImageURI(tmp_fileUri);

                String selectedImagePath = getPath(tmp_fileUri);
                lastPhotoFile = new File(selectedImagePath);
            }

        }
    };
    public String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        @SuppressWarnings("deprecation")
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    // Save the current drawing to sd card
    protected void saveImage() {
        try {
            String name = "FSM" + System.currentTimeMillis() + ".png";
            lastPhotoFile = new File(Environment.getExternalStorageDirectory(), name);
            FileOutputStream stream = new FileOutputStream(lastPhotoFile);
            baseBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            Toast.makeText(MyActivity.this, "Saved as " + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(MyActivity.this, "Save failed", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    //Reset the drawing
    protected void resetImage() {
        if (baseBitmap != null) {
            baseBitmap = Bitmap.createBitmap(iv_canvas.getWidth(),
                    iv_canvas.getHeight(), Bitmap.Config.ARGB_8888);
            canvas = new Canvas(baseBitmap);
            canvas.drawColor(Color.WHITE);
            iv_canvas.setImageBitmap(baseBitmap);
        }
    }

    // Toggle between eraser and brush
    protected void setEraser() {
        if (erase.isChecked()) {
            setBrushColor();
        } else {
            paint.setColor(Color.WHITE);
        }
    }

    protected void setBrushColor() {
        int location = colorProgress * colorBitmap.getWidth() / 100;
        int pixel = colorBitmap.getPixel(location, 5);
        paint.setColor(pixel);
    }


    protected void setBrushWidth() {
        if (erase.isChecked()) {
            paint.setStrokeWidth(80 * widthProgress / 100);
        } else {
            paint.setStrokeWidth(150 * widthProgress / 100);
        }


    }


    public void onResume() {
        super.onResume();
//        showImage();
    }

    private void showImage() {
        final Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    String svr="www.flickr.com";

                    REST rest=new REST();
                    rest.setHost(svr);

                    //initialize Flickr object with key and rest
                    final Flickr flickr=new Flickr(FlickrHelper.API_KEY,rest);

                    //initialize SearchParameter object, this object stores the search keyword
                    SearchParameters searchParams=new SearchParameters();
                    searchParams.setSort(SearchParameters.INTERESTINGNESS_DESC);

                    //Create tag keyword array
                    String[] tags=new String[]{"cs160fsm"};
                    searchParams.setTags(tags);

                    //Initialize PhotosInterface object
                    PhotosInterface photosInterface=flickr.getPhotosInterface();
                    //Execute search with entered tags
                    PhotoList photoList=photosInterface.search(searchParams,20,1);

                    //get search result and fetch the photo object and get small square imag's url
                    if(photoList!=null){
                        //Get search result and check the size of photo result
                        Random random = new Random();
                        int seed = random.nextInt(photoList.size());
                        //get photo object
                        Photo photo=(Photo)photoList.get(seed);

                        //Get small square url photo
                        InputStream is = photo.getMediumAsStream();
                        final Bitmap bm = BitmapFactory.decodeStream(is);
                        resultImage = bm;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
//                                Bitmap sentToToqBM = bm.copy(Bitmap.Config.ARGB_8888,true);
                                iv_canvas.setImageBitmap(bm);
                                ToqActivity.hasResult = true;
                                ToqActivity.resultBm = bm;
                                Log.e("bitmap", "received bitmap in myactivity");
                                finish();
//                                sentToToqBM.setHeight(288);
//                                sentToToqBM.setWidth(250);
//                                Log.e("bitmap", "received bitmap in myactivity");
//                                Intent intent = new Intent();
//                                intent.setClass(MyActivity.this, ToqActivity.class);
//                                intent.putExtra("bitmap", bm);
//                                Log.e("bitmap", "received bitmap in myactivity2");
//                                startActivity(intent);
//                                Log.e("bitmap", "received bitmap in myactivity3");
//                                finish();
                            }
                        });
                    }
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                } catch (FlickrException e) {
                    e.printStackTrace();
                } catch (IOException e ) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }
    // Initialise the UI
    private void initUI(){
//Initialize a brush with color = red, width = 5
        paint = new Paint();
        paint.setStrokeWidth(20);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);

        // Find the widgets and views
        iv_canvas = (ImageView) findViewById(R.id.iv_canvas);
        this.saveBut = (Button) this.findViewById(R.id.saveBut);
        this.resetBut = (Button) this.findViewById(R.id.resetBut);
        this.submitBut = (Button) this.findViewById(R.id.submitBut);
        this.pickBut = (Button) this.findViewById(R.id.pickBut);
        this.erase = (ToggleButton) this.findViewById(R.id.eraserBut);
        this.colorBar = (SeekBar) this.findViewById(R.id.colorBar);
        this.widthBar = (SeekBar) this.findViewById(R.id.brushBar);

        this.colorProgress = 0;
        this.widthProgress = 20;
        Drawable d = getResources().getDrawable(R.drawable.colorspectrum);
        this.colorBitmap = ((BitmapDrawable) d).getBitmap();

        //Initialize listeners

        colorBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 100) {
                    progress--;
                }
                colorProgress = progress;
                setEraser();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        widthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 0) {
                    progress++;
                }
                widthProgress = progress;
                setBrushWidth();
                setEraser();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    private View.OnClickListener click = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.saveBut:
                    saveImage();
                    break;
                case R.id.resetBut:
                    resetImage();
                    break;
                case R.id.eraserBut:
                    setEraser();
                    setBrushWidth();
                default:
                    break;
            }
        }
    };

    View.OnClickListener mUploadClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (lastPhotoFile == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MyActivity.this);
                builder.setMessage("Choose the photo you want to upload");
                builder.setPositiveButton("Current drawing", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
                        saveImage();
                        Intent intent = new Intent(getApplicationContext(),
                                FlickrjActivity.class);
                        intent.putExtra("flickImagePath", lastPhotoFile.getAbsolutePath());
                        startActivity(intent);
                        showImage();
                                                }
                    });
                builder.setNegativeButton("Image from gallery", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Please pick photo",
                                Toast.LENGTH_SHORT).show();
                        }
                    });
                builder.create().show();
            } else {
                Intent intent = new Intent(getApplicationContext(),
                        FlickrjActivity.class);
                intent.putExtra("flickImagePath", lastPhotoFile.getAbsolutePath());
               startActivity(intent);
                showImage();
            }
        }
    };
    View.OnClickListener mPickClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivityForResult(intent, 102);
        }

    };

    private View.OnTouchListener touch = new View.OnTouchListener() {
        float startX;
        float startY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                // 用户按下动作
                case MotionEvent.ACTION_DOWN:
                    // 第一次绘图初始化内存图片，指定背景为白色
                    if (baseBitmap == null) {
                        baseBitmap = Bitmap.createBitmap(iv_canvas.getWidth(),
                                iv_canvas.getHeight(), Bitmap.Config.ARGB_8888);
                        canvas = new Canvas(baseBitmap);
                        canvas.drawColor(Color.WHITE);
                    }
                    // 记录开始触摸的点的坐标
                    startX = event.getX();
                    startY = event.getY();
                    break;
                // 用户手指在屏幕上移动的动作
                case MotionEvent.ACTION_MOVE:
                    // 记录移动位置的点的坐标
                    float stopX = event.getX();
                    float stopY = event.getY();

                    //根据两点坐标，绘制连线
                    canvas.drawLine(startX, startY, stopX, stopY, paint);

                    // 更新开始点的位置
                    startX = event.getX();
                    startY = event.getY();

                    // 把图片展示到ImageView中
                    iv_canvas.setImageBitmap(baseBitmap);
                    break;
                case MotionEvent.ACTION_UP:

                    break;
                default:
                    break;
            }
            return true;
        }
    };

}