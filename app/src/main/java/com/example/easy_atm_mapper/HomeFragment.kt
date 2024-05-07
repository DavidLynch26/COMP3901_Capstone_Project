package com.example.easy_atm_mapper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.easy_atm_mapper.models.Atm
import com.example.easy_atm_mapper.models.MyLatLng
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.Locale
import kotlin.io.encoding.ExperimentalEncodingApi

class HomeFragment : Fragment() {
    private lateinit var view : View
    private lateinit var drawerView : View
    private lateinit var apikey: String
    private var exists: Boolean = false
    private var navigation: Boolean = false

    private lateinit var bottomSheetDialog: BottomSheetDialog

    private val db = Firebase.firestore
    private val banksCollection = db.collection("banks")

    private lateinit var polyline: Polyline
    private var userMarker: Marker? = null
    private var clickedMarker: Marker? = null
    private lateinit var geocoder: Geocoder
    private lateinit var googleMap : GoogleMap
    private lateinit var mapFragment : SupportMapFragment
    private val markerList: MutableList<Triple<Marker,Atm, ImageView>> = mutableListOf()

    private lateinit var autocomplete : AutoCompleteTextView

    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient
    private var interval = listOf(5000.toLong(), 1000.toLong())
    private var priority = listOf(Priority.PRIORITY_BALANCED_POWER_ACCURACY, Priority.PRIORITY_HIGH_ACCURACY)
    private var locationRequest = LocationRequest.Builder(priority[0], interval[0]).build()

//    private lateinit var userAddress: Address

//    private var fineLocPerm : String = android.Manifest.permission.ACCESS_FINE_LOCATION
//    private var coarseLocPerm : String = android.Manifest.permission.ACCESS_COARSE_LOCATION
    private val bankList =
        listOf(
            listOf("Bank of Nova Scotia", "First Caribbean", "Sagicor", "Jamaica National", "National Commercial Bank", "Jamaica Money Market Brokers"),
            listOf("BNS", "CIBC", "Sagicor", "JN", "NCB", "JMMB")
        )
    private val distances = listOf("1","2", "5", "10", "15", "20")

    private lateinit var locationManager: LocationManager
    private var isLocationEnabled: Boolean = false

    private lateinit var dialog: AlertDialog

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_home, container, false)
        drawerView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_layout, null)
        bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(drawerView)

//        val atm = Atm("id", "address2", MyLatLng(17.9,18.9), "busy", 0.0, "Working", "ncb", "image" )
//        makeAtm(atm)
//        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=17.9802057,-76.8747413&radius=20000&type=atm&key=AIzaSyDluFywbkMg_tJGraIbnd6ve7MzCX2vqww"
//        GlobalScope.launch(Dispatchers.IO){
//
//            val response = URL(url).readText()
//        }
//        GlobalScope.launch(Dispatchers.IO){
//            val callback: (String) -> Unit = {
//                result ->
//            }
//            isAtm(atm, callback)
//        }
        apikey = MainActivity.bundle.getString("apikey").toString()

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        dialog = AlertDialog.Builder(requireContext())
            .setTitle("Location Required")
            .setMessage("This app requires access to your location to provide accurate information. Please turn on location services.")
            .setPositiveButton("Turn On")
            { _, _ ->
                // Open the location settings screen
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                requireContext().startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        geocoder = Geocoder(requireContext(), Locale.getDefault())

        GlobalScope.launch(Dispatchers.Main) {
            mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.getMapAsync {
                googleMap = it
                userMarker = googleMap.addMarker(MarkerOptions()
                    .position(LatLng(0.0, 0.0))
                    .title("Current Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
                googleMap.setOnMarkerClickListener { marker->
                    showAtm(marker)
                    clickedMarker = marker
                    true
                }
            }
        }

        if (!isLocationEnabled) {
            dialog.show()
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                if (p0.lastLocation != null) {
                    if (userMarker != null) {
                        GlobalScope.launch(Dispatchers.Main) {
                            updateUI(p0.lastLocation!!)
                        }
                    }
                }
            }
        }

        drawerView.findViewById<Button>(R.id.buttonStartNavigation).setOnClickListener{
            if(!navigation){
                startNavigation()
            } else{
                stopNavigation()
            }
        }

        view.findViewById<ImageButton>(R.id.imageButtonRecentre).setOnClickListener {
            if (userMarker != null) {
                GlobalScope.launch(Dispatchers.Main) {
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            userMarker!!.position,
                            17f
                        ), 2000, null
                    )
                }
            }
        }

        val bankChoice = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextViewBanks)
        val distanceChoice = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextViewDistance)

        autocomplete = bankChoice
        var arrayadap = ArrayAdapter(requireContext(), R.layout.dropdown_banks, bankList[0])
        autocomplete.setAdapter(arrayadap)

        autocomplete = distanceChoice
        arrayadap = ArrayAdapter(requireContext(), R.layout.dropdown_distances, distances)
        autocomplete.setAdapter(arrayadap)

        view.findViewById<Button>(R.id.buttonSearch).setOnClickListener {
            val selectBank = bankChoice.text.toString()
            val selectDistance = distanceChoice.text.toString()

            fun isEmpty(): Boolean {
                var empty = false
                if (selectBank == "Select Bank") {
                    empty = true
                }

                if (selectDistance == "Select Distance") {
                    empty = true
                }
                return empty
            }

            if (!isEmpty()) {
//                getATMAddress(selectBank, selectDistance.toInt() * 1000)
                getAtmGM(selectBank, selectDistance.toInt()*1000)
            }
//                getStreetAddress(userMarker!!.position)
        }
        return view
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun showAtm(marker: Marker) {
        try{
            lateinit var atm: Atm

            for(curr in markerList){
                if(marker == curr.first){
                    atm = curr.second
//                    drawerView.findViewById<ImageView>(R.id.imageViewAtm) = curr.third.image!!
                    drawerView.findViewById<TextView>(R.id.textViewBusyness).text = atm.busyness
                    drawerView.findViewById<TextView>(R.id.textViewAddress).text = atm.address
                    drawerView.findViewById<TextView>(R.id.textViewWorking).text = atm.working
                }
            }
            bottomSheetDialog.show()
        } catch(e: Exception){
            e.printStackTrace()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun getAtmImage(photoReference: String, image: ImageView): ImageView {
        withContext(Dispatchers.IO){
            val url = "https://maps.googleapis.com/maps/api/place/photo?" +
                    "maxwidth=400&" +
                    "photo_reference=$photoReference&" +
                    "key=$apikey"

            val photoData = URL(url).readText()
            try{
//                val decodedBytes = Base64.decode(photoData)
//                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
//                image.setImageBitmap(bitmap)
//                Glide.with(requireContext())
//                    .load(photoData)
//                    .diskCacheStrategy(DiskCacheStrategy.ALL)
//                    .into(image)
//                Log.d("mytagphoto", photoReference)

            } catch(e: Exception){
                e.printStackTrace()
            }


//            val gson = GsonBuilder().create()
//            val root = gson.fromJson(response, JsonObject::class.java)
        }
        Log.d("mytagimage", image.toString())
        return image

    }private suspend fun getPhotoReference(placeId: String): String {
        var photoReference = ""
        withContext(Dispatchers.IO){
            val url = "https://maps.googleapis.com/maps/api/place/details/json?" +
                    "placeid=${placeId}&" +
                    "key=$apikey"
            val response = URL(url).readText()
            val gson = GsonBuilder().create()
            val root = gson.fromJson(response, JsonObject::class.java)

            if(root.asJsonObject
                    .get("result").asJsonObject
                    .get("photos") != null){

                photoReference = root.asJsonObject
                    .get("result").asJsonObject
                    .get("photos").asJsonArray[0].asJsonObject
                    .get("photo_reference").asString
            }
        }
        return photoReference
    }

//    @SuppressLint("MissingPermission")
//    fun getLocation() {
//        fusedLocationProviderClient.lastLocation.addOnSuccessListener {
//            location: Location? ->
//            if (location != null) {
////                if (userMarker != null) {
////                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userMarker.position, 15f))
////                }
//                updateUI(location)
//            }
//        }
////        Log.d("mytagmarker", marker.toString())
//    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private suspend fun updateUI(location: Location) {
//        if (userMarker!!.position.latitude == 0.0 && userMarker!!.position.longitude == 0.0) {
//            googleMap.animateCamera()
//        }
        val latLng = LatLng(location.latitude, location.longitude)
        Log.d("mytaguser", userMarker?.position.toString())
        withContext(Dispatchers.Main) {
            userMarker?.position = latLng

            if (navigation){
                getRoute(clickedMarker!!.position)
            }

            view.findViewById<TextView>(R.id.textViewCurrLocation).text = userMarker?.position.toString()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun getRoute(latLng: LatLng) {
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${userMarker!!.position.latitude},${userMarker!!.position.longitude}&" +
                    "destination=${latLng.latitude},${latLng.longitude}&" +
                    "sensor=false&" +
                    "mode=driving&" +
                    "key=$apikey"
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = URL(url).readText()
                val gson = GsonBuilder().create()
                val root = gson.fromJson(response, JsonObject::class.java)

                withContext(Dispatchers.Main) {
                    val encodedPolyline = root.getAsJsonObject().get("routes").asJsonArray[0].asJsonObject.get("overview_polyline").asJsonObject.get("points").asString
                    val decodedPolyline = PolyUtil.decode(encodedPolyline)
                    polyline = googleMap.addPolyline(PolylineOptions().addAll(decodedPolyline))
                    val bounds = LatLngBounds.Builder().include(decodedPolyline.first())
                        .include(decodedPolyline.last()).build()
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun getAtmGM(selectBank: String, radius: Int) {
        suspend fun addAtm(result: JsonArray){
            try {
                if (result.size() > 0) {
                    val pb = (context as MainActivity)
                        .findViewById<ProgressBar>(R.id.progressBar)
                    pb.visibility = View.VISIBLE
                    for (marker in markerList) {
                        marker.first.remove()
                    }
                    markerList.clear()
                    for (r in result) {
                        val index = bankList[0].indexOf(selectBank)
                        val name = r.asJsonObject.get("name").asString
//                        Log.d("mytagfound", r.asJsonObject.toString())
//                        Log.d("mytagfound", r.asJsonObject.get("vicinity").asString)
                        if (name.contains(bankList[0][index]) || name.contains(bankList[1][index])) {
                            val id = r.asJsonObject.get("place_id").asString
                            val isAtm = isAtm(id, selectBank)

                            var atm: Atm

                            if (isAtm) {
                                atm = getAtmDB(id, selectBank)
                            } else {
                                val location = LatLng(
                                    r.asJsonObject.get("geometry").asJsonObject.get("location").asJsonObject.get(
                                        "lat"
                                    ).asDouble,
                                    r.asJsonObject.get("geometry").asJsonObject.get("location").asJsonObject.get(
                                        "lng"
                                    ).asDouble,
                                )

                                val responseAd = GlobalScope.async {
                                    getAtmAddress(location)
                                }
                                val address = responseAd.await()

                                val responseReference = GlobalScope.async {
                                    getPhotoReference(id)
                                }

                                val photoReference = responseReference.await()

                                atm = Atm(
                                    id,
                                    address,
                                    MyLatLng(location.latitude, location.longitude),
                                    "Not Busy",
                                    0.0,
                                    "Working",
                                    selectBank,
                                    photoReference
                                )
                                makeAtm(atm)
                            }

                            addAtmToMap(name, atm)

                            Log.d("mytagatm", markerList.toString())

//                            val responseImage = GlobalScope.async {
//                                getAtmImage(atm.photoReference!!, markerList.last().third)
//                            }
//
//                            val image = responseImage.await()
//                            Log.d("mytagphoto", image.toString())
                        }
                    }
                    if (markerList != null) {
                        val builder = LatLngBounds.Builder()
                        for (marker in markerList) {
                            Log.d("mytagmarkers", marker.toString())
                            val location =
                                LatLng(
                                    marker.second.location!!.latitude,
                                    marker.second.location!!.longitude
                                )
                            builder.include(location)
                        }
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(
                                builder.build(),
                                100
                            )
                        )
                    }
                    pb.visibility = View.GONE
                }
            } catch(e: Exception){
                e.printStackTrace()
            }
        }
        var url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                "location=${userMarker!!.position.latitude},${userMarker!!.position.longitude}&" +
                "radius=${radius}&" +
                "type=atm&" +
                "key=$apikey"
        Log.d("mytagurl", url)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                var response = URL(url).readText()

                var gson = GsonBuilder().create()
                var root = gson.fromJson(response, JsonObject::class.java)
                var jsonArray = root.getAsJsonObject().get("results").asJsonArray

                var nextPageToken = root.asJsonObject.get("next_page_token")

                Log.d("mytagarray", jsonArray.toString())
                while (nextPageToken != null) {
                    Thread.sleep(2000)
                    url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                            "pagetoken=${nextPageToken.asString}&" +
                            "key=$apikey"

                    response = URL(url).readText()
                    gson = GsonBuilder().create()
                    root = gson.fromJson(response, JsonObject::class.java)

                    jsonArray.addAll(root.getAsJsonObject().get("results").asJsonArray)

                    nextPageToken = root.asJsonObject.get("next_page_token")
                }

                withContext(Dispatchers.Main) {
                    addAtm(jsonArray)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun makeAtm(atm: Atm){
        GlobalScope.launch(Dispatchers.IO) {
            banksCollection.document(atm.bank!!).collection("atms").document()
                .set(atm)
                .addOnSuccessListener {
                    Log.d("mytagCreatedAtm", atm.toString())
                }
                .addOnFailureListener{

                }
        }
    }

    private suspend fun isAtm(id: String, bank: String): Boolean{
        var exists = false
        withContext(Dispatchers.IO){
            banksCollection.document(bank).collection("atms").get()
                .addOnSuccessListener { querySnapshot ->
                    for(document in querySnapshot.documents){
                        val found = document.toObject<Atm>()
                        if (found != null) {
                            if(id == found.id){
                                exists = true
                            }
                        }
                    }
                }.await()
        }
        return exists
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun getAtmDB(id: String, bank: String): Atm{
        var atm = Atm()
        withContext(Dispatchers.IO){
            banksCollection.document(bank).collection("atms").get()
                .addOnSuccessListener { querySnapshot ->
                    for(document in querySnapshot.documents){
                        val found = document.toObject<Atm>()
                        if(found != null){
                            if(id == found.id){
                                atm = found
                            }
                        }
                    }
                }.await()
        }
        return atm
    }

    private suspend fun getAtmAddress(latlng: LatLng): String{
        var address = ""
        val url = "https://maps.googleapis.com/maps/api/geocode/json?" +
                "latlng=${latlng.latitude},${latlng.longitude}&" +
                "key=$apikey"
        withContext(Dispatchers.IO){
            val response = URL(url).readText()
            val gson = GsonBuilder().create()
            val root = gson.fromJson(response, JsonObject::class.java)
            address = root.getAsJsonArray("results")[0].asJsonObject.get("formatted_address").asString
        }
        return address
    }

    @SuppressLint("MissingPermission")
    private fun stopNavigation(){
        navigation = !navigation

        locationRequest = LocationRequest.Builder(priority[0], interval[0]).build()

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())


        polyline.remove()

        drawerView.findViewById<Button>(R.id.buttonStartNavigation).text = "Start Navigation"

//        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startNavigation(){
        navigation = !navigation

        locationRequest = LocationRequest.Builder(priority[1], interval[1]).build()

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        for(marker in markerList){
            if (marker.first != clickedMarker){
                marker.first.remove()
            }
        }
        getRoute(clickedMarker!!.position)

        drawerView.findViewById<Button>(R.id.buttonStartNavigation).text = "Stop Navigation"
//        drawerView.findViewById<Button>(R.id.buttonStartNavigation).text = "Stop Navigation"
    }

    private fun addAtmToMap(name: String, atm: Atm){
        markerList.add(
            Triple(
                googleMap.addMarker(
                    MarkerOptions()
                        .title (name)
                        .position(
                            LatLng(
                                atm.location!!.latitude,
                                atm.location!!.longitude
                            )
                        )
                        .snippet(atm.address)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.atm_icon))
                )!!, atm, ImageView(requireContext())
            )
        )
    }
}