package com.example.easy_atm_mapper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.easy_atm_mapper.models.Atm
import com.example.easy_atm_mapper.models.MyLatLng
import com.example.easy_atm_mapper.models.UserComment
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
import java.net.URL
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
    private val markerList: MutableList<Triple<Marker,Atm, Bitmap>> = mutableListOf()

    private lateinit var autocomplete : AutoCompleteTextView

    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient
    private var interval = listOf(5000.toLong(), 1000.toLong())
    private var priority = listOf(Priority.PRIORITY_HIGH_ACCURACY, Priority.PRIORITY_HIGH_ACCURACY)
    private var locationRequest = LocationRequest.Builder(priority[0], interval[0]).build()

//    private lateinit var userAddress: Address

//    private var fineLocPerm : String = android.Manifest.permission.ACCESS_FINE_LOCATION
//    private var coarseLocPerm : String = android.Manifest.permission.ACCESS_COARSE_LOCATION
    private val bankList =
        listOf(
            listOf("Bank of Nova Scotia", "First Caribbean", "Sagicor", "Jamaica National", "National Commercial Bank", "Jamaica Money Market Brokers"),
            listOf("BNS", "CIBC", "Sagicor", "JN", "NCB", "JMMB")
        )
    private val distances = listOf("1 km","2 km", "5 km", "10 km", "15 km", "20 km")

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
                    clickedMarker = marker
                    showAtm(marker)
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
            try{
                GlobalScope.launch(Dispatchers.Main){
                    if(!navigation){
                        startNavigation()
                    } else{
                        stopNavigation()
                    }
                }
            }catch(e: Exception){
               e.printStackTrace()
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
            val selectDistance = distanceChoice.text.split(" ")[0]

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
                getAtmGM(selectBank, selectDistance.toInt()*1000)
            }
        }
        return view
    }

    private fun showAtm(marker: Marker) {
        try{
            lateinit var atm: Atm

            for(curr in markerList){
                if(marker == curr.first){
                    atm = curr.second
                    drawerView.findViewById<ImageView>(R.id.imageViewAtm).setImageBitmap(curr.third)
                    drawerView.findViewById<TextView>(R.id.textViewAddress).text = "Address: ${atm.address}"
                    drawerView.findViewById<TextView>(R.id.textViewWorking).text = "Status: ${atm.working}"
                    drawerView.findViewById<TextView>(R.id.textViewWaitTime).text = "Wait Time ${atm.waitTime.toString()} mins"
                }
            }
            bottomSheetDialog.show()
        } catch(e: Exception){
            e.printStackTrace()
        }
    }

    private suspend fun getAtmImage(photoReference: String): Bitmap {
        val url = "https://maps.googleapis.com/maps/api/place/photo?" +
                "maxwidth=400&" +
                "photo_reference=$photoReference&" +
                "key=$apikey"

        return withContext(Dispatchers.IO) {
            var image = BitmapFactory.decodeResource(resources, R.drawable.atm_icon)
            try{
                val photoData = URL(url).readText()
                Log.d("mytag", photoData.isNotEmpty().toString())
                val imageBtyes = Base64.decode(photoData, Base64.DEFAULT)
                image = BitmapFactory.decodeByteArray(imageBtyes, 0, imageBtyes.size)
                image
            }catch(e: Exception){
                e.printStackTrace()
                image
            }finally {
                image
            }
        }
    }

    private suspend fun getPhotoReference(placeId: String): String {
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

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun updateUI(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        userMarker?.position = latLng
        Log.d("mytag", latLng.toString())

        if (navigation){
            try{
                withContext(Dispatchers.Main) {
                    val root = getRoute(clickedMarker!!.position)
                    val encodedPolyline = root.getAsJsonObject().get("routes").asJsonArray[0].asJsonObject.get("overview_polyline").asJsonObject.get("points").asString
                    val decodedPolyline = PolyUtil.decode(encodedPolyline)

                    withContext(Dispatchers.Main) {
                        polyline = googleMap.addPolyline(PolylineOptions().addAll(decodedPolyline))
                        polyline.remove()
                        polyline = googleMap.addPolyline(PolylineOptions().addAll(decodedPolyline))
//                        val bounds = LatLngBounds.Builder().include(decodedPolyline.first())
//                            .include(decodedPolyline.last()).build()
//                        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    }

                    val distance = root.getAsJsonObject().get("routes").asJsonArray[0].asJsonObject.get("legs").asJsonArray[0].asJsonObject.get("distance").asJsonObject.get("value").asDouble

                    if (distance <= 10.0) {
                        val arrivalTime = LocalDateTime.now()
                        stopNavigation()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "You have reached your destination",
                                Toast.LENGTH_SHORT
                            ).show()
                            val myRunnable = Runnable {
                                // The event has occurred
                                var rAtm = Atm()
                                for(marker in markerList) {
                                    if (marker.first == clickedMarker) {
                                        rAtm = marker.second
                                    }
                                }
                                val rDialog: AlertDialog = AlertDialog.Builder(requireContext())
                                    .setTitle("Atm Details")
                                    .setMessage("Was the ${rAtm.bank} ATM on ${rAtm.address} working?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        val userComment = UserComment(
                                            ChronoUnit.MINUTES.between(
                                            arrivalTime,
                                            LocalDateTime.now()),
                                            "Working"
                                        )
                                        GlobalScope.launch(Dispatchers.IO){
                                            makeComment(rAtm, userComment)
                                        }
                                    }
                                    .setNegativeButton("No") { rDialog, _ ->
                                        val userComment = UserComment(
                                            ChronoUnit.MINUTES.between(
                                                arrivalTime,
                                                LocalDateTime.now()),
                                            "Not Working"
                                        )
                                        GlobalScope.launch(Dispatchers.IO){
                                            makeComment(rAtm, userComment)
                                        }
                                        rDialog.dismiss()
                                    }
                                    .create()

                                rDialog.show()
                            }
                            val handler = Handler(Looper.getMainLooper())
                            withContext(Dispatchers.IO) {
                                handler.postDelayed({
                                    myRunnable.run()
                                }, 5000)
                            }
                        }
                    }
                }
            }catch(e: Exception){
                e.printStackTrace()
            }
        }
        withContext(Dispatchers.Main) {
            view.findViewById<TextView>(R.id.textViewCurrLocation).text = userMarker?.position.toString()
        }
    }

    private suspend fun getRoute(latLng: LatLng): JsonObject {
        val url: String = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${userMarker!!.position.latitude},${userMarker!!.position.longitude}&" +
                "destination=${latLng.latitude},${latLng.longitude}&" +
                "sensor=false&" +
                "mode=walking&" +
                "key=$apikey"

        return withContext(Dispatchers.IO){
            val response = URL(url).readText()
            val gson = GsonBuilder().create()
            val root = gson.fromJson(response, JsonObject::class.java)
            root
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun makeComment(atm: Atm, userComment: UserComment){
        withContext(Dispatchers.IO) {
            banksCollection.document(atm.bank!!).collection("atms").document(atm.id!!).collection("comments").document()
                .set(userComment)
                .addOnSuccessListener {
                    GlobalScope.launch(Dispatchers.IO) {
                        updateAtm(atm, userComment)
                    }
                }
        }
    }
    private suspend fun updateAtm(atm: Atm, userComment: UserComment){
        var working: Pair<Int, Int> = Pair(0,0) // working, not working
        var average: Long
        var sum: Double = 0.0
        withContext(Dispatchers.IO){
            banksCollection.document(atm.bank!!).collection("atms").document(atm.id!!).collection("comments")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    for(document in querySnapshot.documents) {
                        Log.d("mytagdocument", document.toString())
                        val comment = document.toObject<UserComment>()
                        if (comment != null) {
                            if (comment.working == "Working") {
                                working = Pair(working.first + 1, 0)
                            } else {
                                working = Pair(0, working.second + 1)
                            }
                            sum += comment.timeTaken!!
                        }
                    }

                    Log.d("mytagsum", sum.toString())
                    Log.d("mytagworking", if(working.first > working.second) "Working" else "Not Working")
                    average = (sum /querySnapshot.size()).toLong()

                    banksCollection.document(atm.bank).collection("atms").document(atm.id)
                        .update(
                            "waitTime", average,
                            "working", if(working.first > working.second) "Working" else "Not Working"
                        )
                    Log.d("mytagavg", average.toString())
                }
                .addOnFailureListener {
                    Log.d("mytagcomment", it.toString())
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
                    withContext(Dispatchers.Main) {
                        pb.visibility = View.VISIBLE
                    }
                    for (marker in markerList) {
                        marker.first.remove()
                    }
                    markerList.clear()
                    for (r in result) {
                        val index = bankList[0].indexOf(selectBank)
                        val name = r.asJsonObject.get("name").asString
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

                            val image = getAtmImage(atm.photoReference!!)

                            Log.d("mytag", atm.toString())

                            val responseMarker = GlobalScope.launch{
                                addAtmToMap(name, atm, image)
                            }

                            Log.d("mytag", atm.toString())
                            Log.d("mytag", markerList.toString())

                            responseMarker.join()
                        }
                    }
                    if (markerList.isNotEmpty()) {
                        val builder = LatLngBounds.Builder()
                        for (marker in markerList) {
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
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "No Atms Found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    withContext(Dispatchers.Main) {
                        pb.visibility = View.GONE
                    }
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
        Log.d("mytag", url)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                var response = URL(url).readText()

                var gson = GsonBuilder().create()
                var root = gson.fromJson(response, JsonObject::class.java)
                var jsonArray = root.getAsJsonObject().get("results").asJsonArray

                var nextPageToken = root.asJsonObject.get("next_page_token")

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
            banksCollection.document(atm.bank!!).collection("atms").document(atm.id!!)
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

    private suspend fun getAtmDB(id: String, bank: String): Atm{
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
    private suspend fun stopNavigation(){
        navigation = !navigation

        locationRequest = LocationRequest.Builder(priority[0], interval[0]).build()

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        polyline.remove()

        drawerView.findViewById<Button>(R.id.buttonStartNavigation).text = "Start Navigation"

//        getAtmGM(
//            view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextViewBanks).text.toString(),
//            view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextViewDistance).text.toString().toInt()*1000
//        )

//        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    private suspend fun startNavigation(){
        navigation = !navigation

        locationRequest = LocationRequest.Builder(priority[1], interval[1]).build()

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        for(marker in markerList){
            if (marker.first != clickedMarker){
                marker.first.remove()
            }
        }
        drawerView.findViewById<Button>(R.id.buttonStartNavigation).text = "Stop Navigation"
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun addAtmToMap(name: String, atm: Atm, image: Bitmap){
        GlobalScope.launch(Dispatchers.Main){
            markerList.add(
                Triple(
                    googleMap.addMarker(
                        MarkerOptions()
                            .title (name)
                            .position(
                                LatLng(
                                    atm.location!!.latitude,
                                    atm.location.longitude
                                )
                            )
                            .snippet(atm.address)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.atm_icon))
                    )!!, atm, image
                )
            )
        }
    }
}