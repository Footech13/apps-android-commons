package fr.free.nrw.commons.nearby;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapboxMapOptions;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.android.telemetry.MapboxTelemetry;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.android.support.DaggerFragment;
import fr.free.nrw.commons.CommonsApplication;
import fr.free.nrw.commons.R;
import fr.free.nrw.commons.Utils;
import fr.free.nrw.commons.auth.LoginActivity;
import fr.free.nrw.commons.bookmarks.locations.BookmarkLocationsDao;
import fr.free.nrw.commons.contributions.ContributionController;
import fr.free.nrw.commons.utils.UriDeserializer;
import fr.free.nrw.commons.utils.ViewUtil;
import timber.log.Timber;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView;

import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static fr.free.nrw.commons.wikidata.WikidataConstants.WIKIDATA_ENTITY_ID_PREF;

public class NearbyMapFragment extends DaggerFragment {

    @Inject
    @Named("application_preferences") SharedPreferences applicationPrefs;
    public MapView mapView;
    private List<NearbyBaseMarker> baseMarkerOptions;
    private fr.free.nrw.commons.location.LatLng curLatLng;
    public fr.free.nrw.commons.location.LatLng[] boundaryCoordinates;

    private View bottomSheetList;
    private View bottomSheetDetails;

    private BottomSheetBehavior bottomSheetListBehavior;
    private BottomSheetBehavior bottomSheetDetailsBehavior;
    private LinearLayout wikipediaButton;
    private LinearLayout wikidataButton;
    private LinearLayout directionsButton;
    private LinearLayout commonsButton;
    private LinearLayout bookmarkButton;
    private FloatingActionButton fabPlus;
    private FloatingActionButton fabCamera;
    private FloatingActionButton fabGallery;
    private FloatingActionButton fabRecenter;
    private View transparentView;
    private TextView description;
    private TextView title;
    private TextView distance;
    private ImageView icon;
    private ImageView bookmarkButtonImage;

    private TextView wikipediaButtonText;
    private TextView wikidataButtonText;
    private TextView commonsButtonText;
    private TextView directionsButtonText;

    private boolean isFabOpen = false;
    private Animation rotate_backward;
    private Animation fab_close;
    private Animation fab_open;
    private Animation rotate_forward;
    private ContributionController controller;
    private DirectUpload directUpload;

    private Place place;
    private Marker selected;
    private Marker currentLocationMarker;
    private MapboxMap mapboxMap;
    private PolygonOptions currentLocationPolygonOptions;

    private boolean isBottomListSheetExpanded;
    private final double CAMERA_TARGET_SHIFT_FACTOR_PORTRAIT = 0.06;
    private final double CAMERA_TARGET_SHIFT_FACTOR_LANDSCAPE = 0.04;

    private boolean isSecondMaterialShowcaseDismissed;
    private boolean isMapReady;
    private MaterialShowcaseView thirdSingleShowCaseView;

    private Bundle bundleForUpdtes;// Carry information from activity about changed nearby places and current location

    @Inject
    @Named("prefs")
    SharedPreferences prefs;
    @Inject
    @Named("direct_nearby_upload_prefs")
    SharedPreferences directPrefs;
    @Inject
    BookmarkLocationsDao bookmarkLocationDao;

    public NearbyMapFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.d("Nearby map fragment created");

        controller = new ContributionController(this);
        directUpload = new DirectUpload(this, controller);

        Bundle bundle = this.getArguments();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Uri.class, new UriDeserializer())
                .create();
        if (bundle != null) {
            String gsonPlaceList = bundle.getString("PlaceList");
            String gsonLatLng = bundle.getString("CurLatLng");
            Type listType = new TypeToken<List<Place>>() {
            }.getType();
            String gsonBoundaryCoordinates = bundle.getString("BoundaryCoord");
            List<Place> placeList = gson.fromJson(gsonPlaceList, listType);
            Type curLatLngType = new TypeToken<fr.free.nrw.commons.location.LatLng>() {
            }.getType();
            Type gsonBoundaryCoordinatesType = new TypeToken<fr.free.nrw.commons.location.LatLng[]>() {}.getType();
            curLatLng = gson.fromJson(gsonLatLng, curLatLngType);
            baseMarkerOptions = NearbyController
                    .loadAttractionsFromLocationToBaseMarkerOptions(curLatLng,
                            placeList,
                            getActivity());
            boundaryCoordinates = gson.fromJson(gsonBoundaryCoordinates, gsonBoundaryCoordinatesType);
        }
        if (curLatLng != null) {
            Mapbox.getInstance(getActivity(),
                    getString(R.string.mapbox_commons_app_token));
            MapboxTelemetry.getInstance().setTelemetryEnabled(false);
        }
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Timber.d("onCreateView called");
        if (curLatLng != null) {
            Timber.d("curLatLng found, setting up map view...");
            setupMapView(savedInstanceState);
        }

        setHasOptionsMenu(false);

        return mapView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.getView().setFocusableInTouchMode(true);
        this.getView().requestFocus();
        this.getView().setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (bottomSheetDetailsBehavior.getState() == BottomSheetBehavior
                        .STATE_EXPANDED) {
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    return true;
                } else if (bottomSheetDetailsBehavior.getState() == BottomSheetBehavior
                        .STATE_COLLAPSED) {
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    mapView.getMapAsync(MapboxMap::deselectMarkers);
                    selected = null;
                    return true;
                }
            }
            return false;
        });
    }

    public void updateMapSlightly() {
        if (mapboxMap != null) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Uri.class, new UriDeserializer())
                    .create();
            if (bundleForUpdtes != null) {
                String gsonLatLng = bundleForUpdtes.getString("CurLatLng");
                Type curLatLngType = new TypeToken<fr.free.nrw.commons.location.LatLng>() {}.getType();
                curLatLng = gson.fromJson(gsonLatLng, curLatLngType);
            }
            updateMapToTrackPosition();
        }

    }

    public void updateMapSignificantly() {
        if (mapboxMap != null) {
            if (bundleForUpdtes != null) {
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(Uri.class, new UriDeserializer())
                        .create();

                String gsonPlaceList = bundleForUpdtes.getString("PlaceList");
                String gsonLatLng = bundleForUpdtes.getString("CurLatLng");
                String gsonBoundaryCoordinates = bundleForUpdtes.getString("BoundaryCoord");
                Type listType = new TypeToken<List<Place>>() {}.getType();
                List<Place> placeList = gson.fromJson(gsonPlaceList, listType);
                Type curLatLngType = new TypeToken<fr.free.nrw.commons.location.LatLng>() {}.getType();
                Type gsonBoundaryCoordinatesType = new TypeToken<fr.free.nrw.commons.location.LatLng[]>() {}.getType();
                curLatLng = gson.fromJson(gsonLatLng, curLatLngType);
                baseMarkerOptions = NearbyController
                        .loadAttractionsFromLocationToBaseMarkerOptions(curLatLng,
                                placeList,
                                getActivity());
                boundaryCoordinates = gson.fromJson(gsonBoundaryCoordinates, gsonBoundaryCoordinatesType);
            }
            mapboxMap.clear();
            addCurrentLocationMarker(mapboxMap);
            updateMapToTrackPosition();
            addNearbyMarkerstoMapBoxMap();
        }
    }

    // Only update current position marker and camera view
    private void updateMapToTrackPosition() {

        if (currentLocationMarker != null) {
            LatLng curMapBoxLatLng = new LatLng(curLatLng.getLatitude(),curLatLng.getLongitude());
            ValueAnimator markerAnimator = ObjectAnimator.ofObject(currentLocationMarker, "position",
                    new LatLngEvaluator(), currentLocationMarker.getPosition(),
                    curMapBoxLatLng);
            markerAnimator.setDuration(1000);
            markerAnimator.start();

            List<LatLng> circle = createCircleArray(curLatLng.getLatitude(), curLatLng.getLongitude(),
                    curLatLng.getAccuracy() * 2, 100);
            if (currentLocationPolygonOptions != null){
                mapboxMap.removePolygon(currentLocationPolygonOptions.getPolygon());
                currentLocationPolygonOptions = new PolygonOptions()
                        .addAll(circle)
                        .strokeColor(Color.parseColor("#55000000"))
                        .fillColor(Color.parseColor("#11000000"));
                mapboxMap.addPolygon(currentLocationPolygonOptions);
            }

                // Make camera to follow user on location change
                CameraPosition position ;
                if(ViewUtil.isPortrait(getActivity())){
                    position = new CameraPosition.Builder()
                            .target(isBottomListSheetExpanded ?
                                    new LatLng(curMapBoxLatLng.getLatitude()- CAMERA_TARGET_SHIFT_FACTOR_PORTRAIT,
                                            curMapBoxLatLng.getLongitude())
                                    : curMapBoxLatLng ) // Sets the new camera position
                            .zoom(isBottomListSheetExpanded ?
                                    11 // zoom level is fixed to 11 when bottom sheet is expanded
                                    :mapboxMap.getCameraPosition().zoom) // Same zoom level
                            .build();
                }else {
                    position = new CameraPosition.Builder()
                            .target(isBottomListSheetExpanded ?
                                    new LatLng(curMapBoxLatLng.getLatitude()- CAMERA_TARGET_SHIFT_FACTOR_LANDSCAPE,
                                            curMapBoxLatLng.getLongitude())
                                    : curMapBoxLatLng ) // Sets the new camera position
                            .zoom(isBottomListSheetExpanded ?
                                    11 // zoom level is fixed to 11 when bottom sheet is expanded
                                    :mapboxMap.getCameraPosition().zoom) // Same zoom level
                            .build();
                }

                mapboxMap.animateCamera(CameraUpdateFactory
                        .newCameraPosition(position), 1000);

        }
    }

    private void updateMapCameraAccordingToBottomSheet(boolean isBottomListSheetExpanded) {
        CameraPosition position;
        this.isBottomListSheetExpanded = isBottomListSheetExpanded;
        if (mapboxMap != null && curLatLng != null) {
            if (isBottomListSheetExpanded) {
                // Make camera to follow user on location change
                if(ViewUtil.isPortrait(getActivity())) {
                    position = new CameraPosition.Builder()
                            .target(new LatLng(curLatLng.getLatitude() - CAMERA_TARGET_SHIFT_FACTOR_PORTRAIT,
                                    curLatLng.getLongitude())) // Sets the new camera target above
                            // current to make it visible when sheet is expanded
                            .zoom(11) // Fixed zoom level
                            .build();
                } else {
                    position = new CameraPosition.Builder()
                            .target(new LatLng(curLatLng.getLatitude() - CAMERA_TARGET_SHIFT_FACTOR_LANDSCAPE,
                                    curLatLng.getLongitude())) // Sets the new camera target above
                            // current to make it visible when sheet is expanded
                            .zoom(11) // Fixed zoom level
                            .build();
                }

            } else {
                // Make camera to follow user on location change
                position = new CameraPosition.Builder()
                        .target(new LatLng(curLatLng.getLatitude(),
                                curLatLng.getLongitude())) // Sets the new camera target to curLatLng
                        .zoom(mapboxMap.getCameraPosition().zoom) // Same zoom level
                        .build();
            }
            mapboxMap.animateCamera(CameraUpdateFactory
                    .newCameraPosition(position), 1000);
        }
    }

    private void initViews() {
        bottomSheetList = getActivity().findViewById(R.id.bottom_sheet);
        bottomSheetListBehavior = BottomSheetBehavior.from(bottomSheetList);
        bottomSheetDetails = getActivity().findViewById(R.id.bottom_sheet_details);
        bottomSheetDetailsBehavior = BottomSheetBehavior.from(bottomSheetDetails);
        bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        bottomSheetDetails.setVisibility(View.VISIBLE);

        fabPlus = getActivity().findViewById(R.id.fab_plus);
        fabCamera = getActivity().findViewById(R.id.fab_camera);
        fabGallery = getActivity().findViewById(R.id.fab_galery);
        fabRecenter = getActivity().findViewById(R.id.fab_recenter);

        fab_open = AnimationUtils.loadAnimation(getActivity(), R.anim.fab_open);
        fab_close = AnimationUtils.loadAnimation(getActivity(), R.anim.fab_close);
        rotate_forward = AnimationUtils.loadAnimation(getActivity(), R.anim.rotate_forward);
        rotate_backward = AnimationUtils.loadAnimation(getActivity(), R.anim.rotate_backward);

        transparentView = getActivity().findViewById(R.id.transparentView);

        description = getActivity().findViewById(R.id.description);
        title = getActivity().findViewById(R.id.title);
        distance = getActivity().findViewById(R.id.category);
        icon = getActivity().findViewById(R.id.icon);

        wikidataButton = getActivity().findViewById(R.id.wikidataButton);
        wikipediaButton = getActivity().findViewById(R.id.wikipediaButton);
        directionsButton = getActivity().findViewById(R.id.directionsButton);
        commonsButton = getActivity().findViewById(R.id.commonsButton);

        wikidataButtonText = getActivity().findViewById(R.id.wikidataButtonText);
        wikipediaButtonText = getActivity().findViewById(R.id.wikipediaButtonText);
        directionsButtonText = getActivity().findViewById(R.id.directionsButtonText);
        commonsButtonText = getActivity().findViewById(R.id.commonsButtonText);

        bookmarkButton = getActivity().findViewById(R.id.bookmarkButton);
        bookmarkButtonImage = getActivity().findViewById(R.id.bookmarkButtonImage);

    }

    private void setListeners() {
        fabPlus.setOnClickListener(view -> {
            if (applicationPrefs.getBoolean("login_skipped", false)) {
                // prompt the user to login
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.login_alert_message)
                        .setPositiveButton(R.string.login, (dialog, which) -> {
                            // logout of the app
                            BaseLogoutListener logoutListener = new BaseLogoutListener();
                            CommonsApplication app = (CommonsApplication) getActivity().getApplication();
                            app.clearApplicationData(getContext(), logoutListener);

                        })
                        .show();
            }else {
                animateFAB(isFabOpen);
            }
        });

        bottomSheetDetails.setOnClickListener(view -> {
            if (bottomSheetDetailsBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        fabRecenter.setOnClickListener(view -> {
            if (curLatLng != null) {
                mapView.getMapAsync(mapboxMap -> {
                    CameraPosition position;

                    if(ViewUtil.isPortrait(getActivity())){
                        position = new CameraPosition.Builder()
                                .target(isBottomListSheetExpanded ?
                                        new LatLng(curLatLng.getLatitude()- CAMERA_TARGET_SHIFT_FACTOR_PORTRAIT,
                                                curLatLng.getLongitude())
                                        : new LatLng(curLatLng.getLatitude(), curLatLng.getLongitude(), 0)) // Sets the new camera position
                                .zoom(isBottomListSheetExpanded ?
                                        11 // zoom level is fixed to 11 when bottom sheet is expanded
                                        :mapboxMap.getCameraPosition().zoom) // Same zoom level
                                .build();
                    }else {
                        position = new CameraPosition.Builder()
                                .target(isBottomListSheetExpanded ?
                                        new LatLng(curLatLng.getLatitude()- CAMERA_TARGET_SHIFT_FACTOR_LANDSCAPE,
                                                curLatLng.getLongitude())
                                        : new LatLng(curLatLng.getLatitude(), curLatLng.getLongitude(), 0)) // Sets the new camera position
                                .zoom(isBottomListSheetExpanded ?
                                        11 // zoom level is fixed to 11 when bottom sheet is expanded
                                        :mapboxMap.getCameraPosition().zoom) // Same zoom level
                                .build();
                    }

                    mapboxMap.animateCamera(CameraUpdateFactory
                            .newCameraPosition(position), 1000);

                });
            }
        });

        bottomSheetDetailsBehavior.setBottomSheetCallback(new BottomSheetBehavior
                .BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                prepareViewsForSheetPosition(newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (slideOffset >= 0) {
                    transparentView.setAlpha(slideOffset);
                    if (slideOffset == 1) {
                        transparentView.setClickable(true);
                    } else if (slideOffset == 0) {
                        transparentView.setClickable(false);
                    }
                }
            }
        });

        bottomSheetListBehavior.setBottomSheetCallback(new BottomSheetBehavior
                .BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    updateMapCameraAccordingToBottomSheet(true);
                } else {
                    updateMapCameraAccordingToBottomSheet(false);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

        // Remove button text if they exceed 1 line or if internal layout has not been built
        // Only need to check for directions button because it is the longest
        if (directionsButtonText.getLineCount() > 1 || directionsButtonText.getLineCount() == 0) {
            wikipediaButtonText.setVisibility(View.GONE);
            wikidataButtonText.setVisibility(View.GONE);
            commonsButtonText.setVisibility(View.GONE);
            directionsButtonText.setVisibility(View.GONE);
        }
    }

    private void setupMapView(Bundle savedInstanceState) {
        MapboxMapOptions options = new MapboxMapOptions()
                .compassGravity(Gravity.BOTTOM | Gravity.LEFT)
                .compassMargins(new int[]{12, 0, 0, 24})
                .styleUrl(Style.OUTDOORS)
                .logoEnabled(false)
                .attributionEnabled(false)
                .camera(new CameraPosition.Builder()
                        .target(new LatLng(curLatLng.getLatitude(), curLatLng.getLongitude()))
                        .zoom(11)
                        .build());

        // create map
        mapView = new MapView(getActivity(), options);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                ((NearbyActivity)getActivity()).setMapViewTutorialShowCase();
                NearbyMapFragment.this.mapboxMap = mapboxMap;
                updateMapSignificantly();
            }
        });
        mapView.setStyleUrl("asset://mapstyle.json");
    }

    /**
     * onLogoutComplete is called after shared preferences and data stored in local database are cleared.
     */
    private class BaseLogoutListener implements CommonsApplication.LogoutListener {
        @Override
        public void onLogoutComplete() {
            Timber.d("Logout complete callback received.");
            Intent nearbyIntent = new Intent( getActivity(), LoginActivity.class);
            nearbyIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            nearbyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(nearbyIntent);
            getActivity().finish();
        }
    }

    /**
     * Adds a marker for the user's current position. Adds a
     * circle which uses the accuracy * 2, to draw a circle
     * which represents the user's position with an accuracy
     * of 95%.
     *
     * Should be called only on creation of mapboxMap, there
     * is other method to update markers location with users
     * move.
     */
    private void addCurrentLocationMarker(MapboxMap mapboxMap) {
        if (currentLocationMarker != null) {
            currentLocationMarker.remove(); // Remove previous marker, we are not Hansel and Gretel
        }

        Icon icon = IconFactory.getInstance(getContext()).fromResource(R.drawable.current_location_marker);

        MarkerOptions currentLocationMarkerOptions = new MarkerOptions()
                .position(new LatLng(curLatLng.getLatitude(), curLatLng.getLongitude()));
        currentLocationMarkerOptions.setIcon(icon); // Set custom icon

        currentLocationMarker = mapboxMap.addMarker(currentLocationMarkerOptions);

        List<LatLng> circle = createCircleArray(curLatLng.getLatitude(), curLatLng.getLongitude(),
                curLatLng.getAccuracy() * 2, 100);

        currentLocationPolygonOptions = new PolygonOptions()
                .addAll(circle)
                .strokeColor(Color.parseColor("#55000000"))
                .fillColor(Color.parseColor("#11000000"));
        mapboxMap.addPolygon(currentLocationPolygonOptions);
    }

    private void addNearbyMarkerstoMapBoxMap() {

        mapboxMap.addMarkers(baseMarkerOptions);

        mapboxMap.setOnInfoWindowCloseListener(marker -> {
            if (marker == selected) {
                bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });
        mapView.getMapAsync(mapboxMap -> {
            mapboxMap.addMarkers(baseMarkerOptions);
            fabRecenter.setVisibility(View.VISIBLE);
            mapboxMap.setOnInfoWindowCloseListener(marker -> {
                if (marker == selected) {
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            });

            mapboxMap.setOnMarkerClickListener(marker -> {

                if (marker instanceof NearbyMarker) {
                    this.selected = marker;
                    NearbyMarker nearbyMarker = (NearbyMarker) marker;
                    Place place = nearbyMarker.getNearbyBaseMarker().getPlace();
                    passInfoToSheet(place);
                    bottomSheetListBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    bottomSheetDetailsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

                }
                return false;
            });

        });
    }


    /**
     * Creates a series of points that create a circle on the map.
     * Takes the center latitude, center longitude of the circle,
     * the radius in meter and the number of nodes of the circle.
     *
     * @return List List of LatLng points of the circle.
     */
    private List<LatLng> createCircleArray(
            double centerLat, double centerLong, float radius, int nodes) {
        List<LatLng> circle = new ArrayList<>();
        float radiusKilometer = radius / 1000;
        double radiusLong = radiusKilometer
                / (111.320 * Math.cos(centerLat * Math.PI / 180));
        double radiusLat = radiusKilometer / 110.574;

        for (int i = 0; i < nodes; i++) {
            double theta = ((double) i / (double) nodes) * (2 * Math.PI);
            double nodeLongitude = centerLong + radiusLong * Math.cos(theta);
            double nodeLatitude = centerLat + radiusLat * Math.sin(theta);
            circle.add(new LatLng(nodeLatitude, nodeLongitude));
        }
        return circle;
    }

    public void prepareViewsForSheetPosition(int bottomSheetState) {

        switch (bottomSheetState) {
            case (BottomSheetBehavior.STATE_COLLAPSED):
                closeFabs(isFabOpen);
                if (!fabPlus.isShown()) showFAB();
                this.getView().requestFocus();
                break;
            case (BottomSheetBehavior.STATE_EXPANDED):
                this.getView().requestFocus();
                break;
            case (BottomSheetBehavior.STATE_HIDDEN):
                mapView.getMapAsync(MapboxMap::deselectMarkers);
                transparentView.setClickable(false);
                transparentView.setAlpha(0);
                closeFabs(isFabOpen);
                hideFAB();
                if (this.getView() != null) {
                    this.getView().requestFocus();
                }
                break;
        }
    }

    private void hideFAB() {

        removeAnchorFromFABs(fabPlus);
        fabPlus.hide();

        removeAnchorFromFABs(fabCamera);
        fabCamera.hide();

        removeAnchorFromFABs(fabGallery);
        fabGallery.hide();

    }

    /*
    * We are not able to hide FABs without removing anchors, this method removes anchors
    * */
    private void removeAnchorFromFABs(FloatingActionButton floatingActionButton) {
        //get rid of anchors
        //Somehow this was the only way https://stackoverflow.com/questions/32732932
        // /floatingactionbutton-visible-for-sometime-even-if-visibility-is-set-to-gone
        CoordinatorLayout.LayoutParams param = (CoordinatorLayout.LayoutParams) floatingActionButton
                .getLayoutParams();
        param.setAnchorId(View.NO_ID);
        // If we don't set them to zero, then they become visible for a moment on upper left side
        param.width = 0;
        param.height = 0;
        floatingActionButton.setLayoutParams(param);
    }

    private void showFAB() {

        addAnchorToBigFABs(fabPlus, getActivity().findViewById(R.id.bottom_sheet_details).getId());
        fabPlus.show();

        addAnchorToSmallFABs(fabGallery, getActivity().findViewById(R.id.empty_view).getId());

        addAnchorToSmallFABs(fabCamera, getActivity().findViewById(R.id.empty_view1).getId());
        thirdSingleShowCaseView = new MaterialShowcaseView.Builder(this.getActivity())
                .setTarget(fabPlus)
                .setDismissText(getString(R.string.showcase_view_got_it_button))
                .setContentText(getString(R.string.showcase_view_plus_fab))
                .setDelay(500) // optional but starting animations immediately in onCreate can make them choppy
                .singleUse(ViewUtil.SHOWCASE_VIEW_ID_3) // provide a unique ID used to ensure it is only shown once
                .setDismissStyle(Typeface.defaultFromStyle(Typeface.BOLD))
                .build();

        isMapReady = true;
        if (isSecondMaterialShowcaseDismissed) {
            thirdSingleShowCaseView.show(getActivity());
        }
    }


    /*
    * Add anchors back before making them visible again.
    * */
    private void addAnchorToBigFABs(FloatingActionButton floatingActionButton, int anchorID) {
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams
                (ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setAnchorId(anchorID);
        params.anchorGravity = Gravity.TOP|Gravity.RIGHT|Gravity.END;
        floatingActionButton.setLayoutParams(params);
    }

    /*
    * Add anchors back before making them visible again. Big and small fabs have different anchor
    * gravities, therefore the are two methods.
    * */
    private void addAnchorToSmallFABs(FloatingActionButton floatingActionButton, int anchorID) {
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams
                (ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setAnchorId(anchorID);
        params.anchorGravity = Gravity.CENTER_HORIZONTAL;
        floatingActionButton.setLayoutParams(params);
    }

    private void passInfoToSheet(Place place) {
        this.place = place;

        int bookmarkIcon;
        if (bookmarkLocationDao.findBookmarkLocation(place)) {
            bookmarkIcon = R.drawable.ic_round_star_filled_24px;
        } else {
            bookmarkIcon = R.drawable.ic_round_star_border_24px;
        }
        bookmarkButtonImage.setImageResource(bookmarkIcon);
        bookmarkButton.setOnClickListener(view -> {
            boolean isBookmarked = bookmarkLocationDao.updateBookmarkLocation(place);
            int updatedIcon = isBookmarked ? R.drawable.ic_round_star_filled_24px : R.drawable.ic_round_star_border_24px;
            bookmarkButtonImage.setImageResource(updatedIcon);
        });

        wikipediaButton.setEnabled(place.hasWikipediaLink());
        wikipediaButton.setOnClickListener(view -> openWebView(place.siteLinks.getWikipediaLink()));

        wikidataButton.setEnabled(place.hasWikidataLink());
        wikidataButton.setOnClickListener(view -> openWebView(place.siteLinks.getWikidataLink()));

        directionsButton.setOnClickListener(view -> {
            //Open map app at given position
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, this.place.location.getGmmIntentUri());
            if (mapIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivity(mapIntent);
            }
        });

        commonsButton.setEnabled(place.hasCommonsLink());
        commonsButton.setOnClickListener(view -> openWebView(place.siteLinks.getCommonsLink()));

        icon.setImageResource(place.getLabel().getIcon());

        title.setText(place.name);
        distance.setText(place.distance);
        description.setText(place.getLongDescription());
        title.setText(place.name.toString());
        distance.setText(place.distance.toString());

        fabCamera.setOnClickListener(view -> {
            if (fabCamera.isShown()) {
                Timber.d("Camera button tapped. Place: %s", place.toString());
                storeSharedPrefs();
                directUpload.initiateCameraUpload();
            }
        });

        fabGallery.setOnClickListener(view -> {
            if (fabGallery.isShown()) {
                Timber.d("Gallery button tapped. Place: %s", place.toString());
                storeSharedPrefs();
                directUpload.initiateGalleryUpload();
            }
        });
    }

    void storeSharedPrefs() {
        SharedPreferences.Editor editor = directPrefs.edit();
        editor.putString("Title", place.getName());
        editor.putString("Desc", place.getLongDescription());
        editor.putString("Category", place.getCategory());
        editor.putString(WIKIDATA_ENTITY_ID_PREF, place.getWikiDataEntityId());
        editor.apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Timber.d("onRequestPermissionsResult: req code = " + " perm = " + permissions + " grant =" + grantResults);

        // Do not use requestCode 1 as it will conflict with NearbyActivity's requestCodes
        switch (requestCode) {
            // 4 = "Read external storage" allowed when gallery selected
            case 4: {
                if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                    Timber.d("Call controller.startGalleryPick()");
                    controller.startGalleryPick();
                }
            }
            break;

            // 5 = "Write external storage" allowed when camera selected
            case 5: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.d("Call controller.startCameraCapture()");
                    controller.startCameraCapture();
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        if (resultCode == RESULT_OK) {
            Timber.d("OnActivityResult() parameters: Req code: %d Result code: %d Data: %s",
                    requestCode, resultCode, data);
            String wikidataEntityId = directPrefs.getString("WikiDataEntityId", null);
            if (requestCode == ContributionController.SELECT_FROM_CAMERA) {
                // If coming from camera, pass null as uri. Because camera photos get saved to a
                // fixed directory
                controller.handleImagePicked(requestCode, null, true, wikidataEntityId);
            } else {
                controller.handleImagePicked(requestCode, data.getData(), true, wikidataEntityId);
            }
        } else {
            Timber.e("OnActivityResult() parameters: Req code: %d Result code: %d Data: %s",
                    requestCode, resultCode, data);
        }
    }

    private void openWebView(Uri link) {
        Utils.handleWebUrl(getContext(), link);
    }

    private void animateFAB(boolean isFabOpen) {
            this.isFabOpen = !isFabOpen;
        if (fabPlus.isShown()){
            if (isFabOpen) {
                fabPlus.startAnimation(rotate_backward);
                fabCamera.startAnimation(fab_close);
                fabGallery.startAnimation(fab_close);
                fabCamera.hide();
                fabGallery.hide();
            } else {
                fabPlus.startAnimation(rotate_forward);
                fabCamera.startAnimation(fab_open);
                fabGallery.startAnimation(fab_open);
                fabCamera.show();
                fabGallery.show();
            }
            this.isFabOpen=!isFabOpen;
        }
    }

    private void closeFabs ( boolean isFabOpen){
        if (isFabOpen) {
            fabPlus.startAnimation(rotate_backward);
            fabCamera.startAnimation(fab_close);
            fabGallery.startAnimation(fab_close);
            fabCamera.hide();
            fabGallery.hide();
            this.isFabOpen = !isFabOpen;
        }
    }

    public void setBundleForUpdtes(Bundle bundleForUpdtes) {
        this.bundleForUpdtes = bundleForUpdtes;
    }

    public void onNearbyMaterialShowcaseDismissed() {
        isSecondMaterialShowcaseDismissed = true;
        if (isMapReady) {
            thirdSingleShowCaseView.show(getActivity());
        }
    }


    @Override
    public void onStart() {
        if (mapView != null) {
            mapView.onStart();
        }
        super.onStart();
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        initViews();
        setListeners();
        transparentView.setClickable(false);
        transparentView.setAlpha(0);
    }

    @Override
    public void onStop() {
        if (mapView != null) {
            mapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        selected = null;
        currentLocationMarker = null;

        super.onDestroyView();
    }

    private static class LatLngEvaluator implements TypeEvaluator<LatLng> {
        // Method is used to interpolate the marker animation.
        private LatLng latLng = new LatLng();

        @Override
        public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
            latLng.setLatitude(startValue.getLatitude()
                    + ((endValue.getLatitude() - startValue.getLatitude()) * fraction));
            latLng.setLongitude(startValue.getLongitude()
                    + ((endValue.getLongitude() - startValue.getLongitude()) * fraction));
            return latLng;
        }
    }
}

