package com.andrewbfang.flickrtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.qualcomm.toq.smartwatch.api.v1.deckofcards.Constants;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener;
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

import java.io.InputStream;
import java.util.Iterator;
import java.util.Random;


public class ToqActivity extends Activity {

    private final static String PREFS_FILE= "prefs_file";
    private final static String DECK_OF_CARDS_KEY= "deck_of_cards_key";
    private final static String DECK_OF_CARDS_VERSION_KEY= "deck_of_cards_version_key";

    private DeckOfCardsManager mDeckOfCardsManager;
    private RemoteDeckOfCards mRemoteDeckOfCards;
    private RemoteResourceStore mRemoteResourceStore;
    private CardImage[] mCardImages;
    private ToqBroadcastReceiver toqReceiver;


    //LocationListener
    private MyLocationListener location;
    private double longitude;
    private double latitude;
    private LocationManager lm;
    private boolean shouldListen;
    private boolean isInstalled;


    //Card Contents
    private String[] names;
    private String[] tasks;
    private int lastNumber;

    public static boolean hasResult = false;
    public static Bitmap resultBm = null;

    @Override
    protected void onResume(){
        super.onResume();
//        init();
        Log.e("Toqact", "Toq.onResume");
        if(hasResult){
            Log.e("card", "has result");
            ((ImageView) findViewById(R.id.resultImage)).setImageBitmap(resultBm);
            addSimpleResultCard();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_toq);
        Log.e("Toqact", "Toq.oncreate");
        mDeckOfCardsManager = DeckOfCardsManager.getInstance(getApplicationContext());
        toqReceiver = new ToqBroadcastReceiver();

        init();
        setupUI();
//        LocationListeners
        location = new MyLocationListener(this);
        lm = (LocationManager) getSystemService(getApplicationContext().LOCATION_SERVICE);
//        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15000, 10, location);

    }

    /**
     * @see android.app.Activity#onStart()
     * This is called after onCreate(Bundle) or after onRestart() if the activity has been stopped
     */
    protected void onStart(){
        super.onStart();

        Log.d(Constants.TAG, "ToqApiDemo.onStart");
        // If not connected, try to connect
        if (!mDeckOfCardsManager.isConnected()){
            try{
                mDeckOfCardsManager.connect();
            }
            catch (RemoteDeckOfCardsException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toq, menu);
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
        return super.onOptionsItemSelected(item);
    }

    private void setupUI() {

        findViewById(R.id.install_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                install();
            }
        });

        findViewById(R.id.uninstall_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uninstall();
            }
        });
    }

    private boolean sendNotification() {
        Random random = new Random();
        lastNumber = Math.abs(random.nextInt() % 6);
        String[] message = new String[2];
        message[0] = "Draw Request";
        message[1] = names[lastNumber];
        // Create a NotificationTextCard
        NotificationTextCard notificationCard = new NotificationTextCard(System.currentTimeMillis(),
                "Free Speech Notification", message);

        // Draw divider between lines of text
        notificationCard.setShowDivider(true);
        // Vibrate to alert user when showing the notification
        notificationCard.setVibeAlert(true);
        // Create a notification with the NotificationTextCard we made
        RemoteToqNotification notification = new RemoteToqNotification(this, notificationCard);

        try {
            // Send the notification
            mDeckOfCardsManager.sendNotification(notification);
//            Toast.makeText(this, "Sent Notification", Toast.LENGTH_SHORT).show();
            Log.e("ac", "sent notification");
            return true;
        } catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
//            Toast.makeText(this, "Failed to send Notification. Please install Toq App", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    /**
     * Installs applet to Toq watch if app is not yet installed
     */
    private void install() {
        isInstalled = true;
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15000, 10, location);

        try {
            isInstalled = mDeckOfCardsManager.isInstalled();
        }
        catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
//            Toast.makeText(this, "Error: Can't determine if app is installed", Toast.LENGTH_SHORT).show();
        }

        if (!isInstalled) {
            try {
                mDeckOfCardsManager.installDeckOfCards(mRemoteDeckOfCards, mRemoteResourceStore);
            } catch (RemoteDeckOfCardsException e) {
                e.printStackTrace();
//                Toast.makeText(this, "Error: Cannot install application", Toast.LENGTH_SHORT).show();
            }
        } else {
//            Toast.makeText(this, "App is already installed!", Toast.LENGTH_SHORT).show();
        }

        try{
            storeDeckOfCards();
        }
        catch (Exception e){
            e.printStackTrace();
        }
        shouldListen = true;

    }

    private void uninstall() {
        boolean isInstalled = true;

        try {
            isInstalled = mDeckOfCardsManager.isInstalled();
        }
        catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: Can't determine if app is installed", Toast.LENGTH_SHORT).show();
        }

        if (isInstalled) {
            try{
                mDeckOfCardsManager.uninstallDeckOfCards();
            }
            catch (RemoteDeckOfCardsException e){
                Toast.makeText(this, getString(R.string.error_uninstalling_deck_of_cards), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, getString(R.string.already_uninstalled), Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * Adds a deck of cards to the applet
     */
    private void addSimpleResultCard() {
//        Toast.makeText(getApplicationContext(), "add result card", Toast.LENGTH_LONG).show();
        ListCard listCard = mRemoteDeckOfCards.getListCard();
        int currSize = listCard.size();
        while (currSize>6){
            listCard.remove(currSize - 1);
            currSize --;
        }
        try {
            mDeckOfCardsManager.updateDeckOfCards(mRemoteDeckOfCards);
        } catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
            Log.e("cards", "Failed to delete Card from ListCard");
        }
        currSize = listCard.size();

        // Create a SimpleTextCard with 1 + the current number of SimpleTextCards
        SimpleTextCard simpleTextCard = new SimpleTextCard(Integer.toString(currSize+1));

        simpleTextCard.setHeaderText("FSM drawing");
        simpleTextCard.setTitleText("#cs160fsm");
        simpleTextCard.setCardImage(mRemoteResourceStore, mCardImages[0]);
        simpleTextCard.setReceivingEvents(false);
        simpleTextCard.setShowDivider(true);

        listCard.add(simpleTextCard);

        try {
            mDeckOfCardsManager.updateDeckOfCards(mRemoteDeckOfCards);
            Log.e("card", "added result card");
        } catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to Create SimpleTextCard", Toast.LENGTH_SHORT).show();
        }

    }


    private void removeDeckOfCards() {
        ListCard listCard = mRemoteDeckOfCards.getListCard();
        if (listCard.size() == 0) {
            return;
        }

        listCard.remove(0);

        try {
            mDeckOfCardsManager.updateDeckOfCards(mRemoteDeckOfCards);
        } catch (RemoteDeckOfCardsException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to delete Card from ListCard", Toast.LENGTH_SHORT).show();
        }

    }

    // Initialise
    private void init() {

        // Create the resource store for icons and images
        mRemoteResourceStore = new RemoteResourceStore();

        DeckOfCardsLauncherIcon whiteIcon = null;
        DeckOfCardsLauncherIcon colorIcon = null;
        names = new String[]{"Mario Savio", "Jack Weinberg", "Joan Baez", "Jackie Goldberg", "Michael Rossmann", "Art Goldberg"};
        tasks = new String[]{"Express your own view of free speech in an image", "Draw Text: FSM", "Draw Image of: A Megaphone", "Draw Text: SLATE", "Draw Text: Free Speech", "Draw Text: Now"};


        // Get the launcher icons
        try {
            whiteIcon = new DeckOfCardsLauncherIcon("white.launcher.icon", getBitmap("bw.png"), DeckOfCardsLauncherIcon.WHITE);
            colorIcon = new DeckOfCardsLauncherIcon("color.launcher.icon", getBitmap("color.png"), DeckOfCardsLauncherIcon.COLOR);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Can't get launcher icon");
            return;
        }

        mCardImages = new CardImage[6];
        try {
            mCardImages[0] = new CardImage("card.image.1", getBitmap("mario_savio.png"));
            mCardImages[1] = new CardImage("card.image.2", getBitmap("jack_weinberg.png"));
            mCardImages[2] = new CardImage("card.image.3", getBitmap("joan_baez.png"));
            mCardImages[3] = new CardImage("card.image.4", getBitmap("jackie_goldberg.png"));
            mCardImages[4] = new CardImage("card.image.5", getBitmap("michael_rossman.png"));
            mCardImages[5] = new CardImage("card.image.6 ", getBitmap("art_goldberg.png"));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Can't get picture icon");
            return;
        }

        // Try to retrieve a stored deck of cards
        try {
            // If there is no stored deck of cards or it is unusable, then create new and store
            if ((mRemoteDeckOfCards = getStoredDeckOfCards()) == null) {
                mRemoteDeckOfCards = createDeckOfCards();
                storeDeckOfCards();
            }
        } catch (Throwable th) {
            th.printStackTrace();
            mRemoteDeckOfCards = null; // Reset to force recreate
        }

        // Make sure in usable state
        if (mRemoteDeckOfCards == null) {
            mRemoteDeckOfCards = createDeckOfCards();
        }

        // Set the custom launcher icons, adding them to the resource store
        mRemoteDeckOfCards.setLauncherIcons(mRemoteResourceStore, new DeckOfCardsLauncherIcon[]{whiteIcon, colorIcon});

        // Re-populate the resource store with any card images being used by any of the cards
        for (Iterator<Card> it = mRemoteDeckOfCards.getListCard().iterator(); it.hasNext(); ) {

            String cardImageId = ((SimpleTextCard) it.next()).getCardImageId();

            if ((cardImageId != null) && !mRemoteResourceStore.containsId(cardImageId)) {

                if (cardImageId.equals("card.image.1")) {
                    mRemoteResourceStore.addResource(mCardImages[0]);
                } else if (cardImageId.equals("card.image.2")) {
                    mRemoteResourceStore.addResource(mCardImages[1]);
                } else if (cardImageId.equals("card.image.3")) {
                    mRemoteResourceStore.addResource(mCardImages[2]);
                } else if (cardImageId.equals("card.image.4")) {
                    mRemoteResourceStore.addResource(mCardImages[3]);
                } else if (cardImageId.equals("card.image.5")) {
                    mRemoteResourceStore.addResource(mCardImages[4]);
                } else if (cardImageId.equals("card.image.6")) {
                    mRemoteResourceStore.addResource(mCardImages[5]);
                }
            }
        }
    }

    // Read an image from assets and return as a bitmap
    private Bitmap getBitmap(String fileName) throws Exception{

        try{
            InputStream is= getAssets().open(fileName);
            return BitmapFactory.decodeStream(is);
        }
        catch (Exception e){
            throw new Exception("An error occurred getting the bitmap: " + fileName, e);
        }
    }

    private RemoteDeckOfCards getStoredDeckOfCards() throws Exception{

        if (!isValidDeckOfCards()){
            Log.w(Constants.TAG, "Stored deck of cards not valid for this version of the demo, recreating...");
            return null;
        }

        SharedPreferences prefs= getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        String deckOfCardsStr= prefs.getString(DECK_OF_CARDS_KEY, null);

        if (deckOfCardsStr == null){
            return null;
        }
        else{
            return ParcelableUtil.unmarshall(deckOfCardsStr, RemoteDeckOfCards.CREATOR);
        }

    }

    /**
     * Uses SharedPreferences to store the deck of cards
     * This is mainly used to
     */
    private void storeDeckOfCards() throws Exception{
        // Retrieve and hold the contents of PREFS_FILE, or create one when you retrieve an editor (SharedPreferences.edit())
        SharedPreferences prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        // Create new editor with preferences above
        SharedPreferences.Editor editor = prefs.edit();
        // Store an encoded string of the deck of cards with key DECK_OF_CARDS_KEY
        editor.putString(DECK_OF_CARDS_KEY, ParcelableUtil.marshall(mRemoteDeckOfCards));
        // Store the version code with key DECK_OF_CARDS_VERSION_KEY
        editor.putInt(DECK_OF_CARDS_VERSION_KEY, Constants.VERSION_CODE);
        // Commit these changes
        editor.commit();
    }

    // Check if the stored deck of cards is valid for this version of the demo
    private boolean isValidDeckOfCards(){

        SharedPreferences prefs= getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        // Return 0 if DECK_OF_CARDS_VERSION_KEY isn't found
        int deckOfCardsVersion= prefs.getInt(DECK_OF_CARDS_VERSION_KEY, 0);

        return deckOfCardsVersion >= Constants.VERSION_CODE;
    }

    // Create some cards with example content
    private RemoteDeckOfCards createDeckOfCards(){
        DeckOfCardsEventListener listener = new DeckOfCardsEventListener() {
            @Override
            public void onCardOpen(String s) {
//                Toast.makeText(getApplicationContext(), "draw", Toast.LENGTH_LONG).show();
                Log.e("ac", "start drawing activity");

                Intent intent = new Intent();
                intent.setClass(ToqActivity.this, MyActivity.class);
                startActivity(intent);
            }

            @Override
            public void onCardVisible(String s) {

            }

            @Override
            public void onCardInvisible(String s) {

            }

            @Override
            public void onCardClosed(String s) {

            }

            @Override
            public void onMenuOptionSelected(String s, String s2) {

            }

            @Override
            public void onMenuOptionSelected(String s, String s2, String s3) {

            }
        };
        ListCard listCard= new ListCard();
        int number;
        SimpleTextCard card;

        for(number = 0; number < 6; number++){
            card = new SimpleTextCard("card" + Integer.toString(number));
            card.setHeaderText(names[number]);
            card.setTitleText(tasks[number]);
            card.setCardImage(mRemoteResourceStore,mCardImages[number]);
            card.setReceivingEvents(true);
            card.setShowDivider(true);
            listCard.add(card);
        }
        mDeckOfCardsManager.addDeckOfCardsEventListener(listener);
        return new RemoteDeckOfCards(this, listCard);
    }

    public class MyLocationListener extends Service implements LocationListener {

        private final Context mContext;

        // flag for GPS status
        boolean isGPSEnabled = false;

        // flag for network status
        boolean isNetworkEnabled = false;

        boolean canGetLocation = false;

        Location location; // location
        double latitude; // latitude
        double longitude; // longitude

        // The minimum distance to change Updates in meters
        private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters

        // The minimum time between updates in milliseconds
        private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 1; // 1 minute

        // Declaring a Location Manager
        protected LocationManager lm;

        public MyLocationListener(Context context) {
            this.mContext = context;
            getLocation();
        }

        public Location getLocation() {
            try {
                lm = (LocationManager) mContext
                        .getSystemService(LOCATION_SERVICE);

                // getting GPS status
                isGPSEnabled = lm
                        .isProviderEnabled(LocationManager.GPS_PROVIDER);

                // getting network status
                isNetworkEnabled = lm
                        .isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (!isGPSEnabled && !isNetworkEnabled) {
                    // no network provider is enabled
                } else {
                    this.canGetLocation = true;
                    // First get location from Network Provider
                    if (isNetworkEnabled) {
                        lm.requestLocationUpdates(
                                LocationManager.NETWORK_PROVIDER,
                                MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
//                        Log.d("Network", "Network");
                        if (lm != null) {
                            location = lm
                                    .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                            }
                        }
                    }
                    // if GPS Enabled get lat/long using GPS Services
                    if (isGPSEnabled) {
                        if (location == null) {
                            lm.requestLocationUpdates(
                                    LocationManager.GPS_PROVIDER,
                                    MIN_TIME_BW_UPDATES,
                                    MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                            Log.d("GPS Enabled", "GPS Enabled");
                            if (lm != null) {
                                location = lm
                                        .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                                if (location != null) {
                                    latitude = location.getLatitude();
                                    longitude = location.getLongitude();
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

//            Log.e("GPS", "location changed, getLocation: lat="+latitude+ ", lon="+longitude);
            return location;
        }
        @Override
        public void onLocationChanged(Location location) {
            getLocation();
            updateLocation();

        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public IBinder onBind(Intent arg0) {
            return null;
        }
        /**
         * Function to get latitude
         * */
        public double getLatitude(){
            if(location != null){
                latitude = location.getLatitude();
            }

            // return latitude
            return latitude;
        }

        /**
         * Function to get longitude
         * */
        public double getLongitude(){
            if(location != null){
                longitude = location.getLongitude();
            }

            // return longitude
            return longitude;
        }
        /**
         * Function to check if best network provider
         * @return boolean
         * */
        public boolean canGetLocation() {
            return this.canGetLocation;
        }

        /**
         * Function to show settings alert dialog
         * */
        public void showSettingsAlert(){
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);

            // Setting Dialog Title
            alertDialog.setTitle("GPS is settings");

            // Setting Dialog Message
            alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");

            // Setting Icon to Dialog
            //alertDialog.setIcon(R.drawable.delete);

            // On pressing Settings button
            alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int which) {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    mContext.startActivity(intent);
                }
            });

            // on pressing cancel button
            alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            // Showing Alert Message
            alertDialog.show();
        }
        /**
         * Stop using GPS listener
         * Calling this function will stop using GPS in your app
         * */
        public void stopUsingGPS(){
            if(lm != null){
                lm.removeUpdates(MyLocationListener.this);
            }
        }
    }

    public void updateLocation(){
        longitude = location.getLongitude();
        latitude = location.getLatitude();
        float[] result = new float[10];
        location.getLocation().distanceBetween(37.86965, -122.25914, latitude, longitude, result);

        if(result[0] <= 5000 && isInstalled){
            location.stopUsingGPS();
            initToqActivity();
        }else{
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15000, 10, location);
        }
    }

    public void initToqActivity(){
        Log.e("ac", "inittoqActivity");
        sendNotification();

        Intent intent = new Intent();
        intent.setClass(ToqActivity.this, MyActivity.class);
//        startActivity(intent);
    }

}
