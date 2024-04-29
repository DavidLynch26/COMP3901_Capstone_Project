package com.example.easy_atm_mapper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Address
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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class HomeFragment : Fragment() {
    private lateinit var view : View
    private lateinit var mapFragment : SupportMapFragment
    private lateinit var googleMap : GoogleMap
    private lateinit var autocomplete : AutoCompleteTextView
    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient
    private val locationRequest = LocationRequest.Builder(102, 5000).build()
    private lateinit var locationCallback: LocationCallback
    private lateinit var geocoder: Geocoder
    private var userMarker: Marker? = null
    private lateinit var apikey: String
//    private lateinit var userAddress: Address
    private val markerList: MutableList<Marker> = mutableListOf()

//    private var fineLocPerm : String = android.Manifest.permission.ACCESS_FINE_LOCATION
//    private var coarseLocPerm : String = android.Manifest.permission.ACCESS_COARSE_LOCATION
    private val banks =
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
        apikey = MainActivity.bundle.getString("apikey").toString()
//        Log.d("mytagapikey", apikey)

        view = inflater.inflate(R.layout.fragment_home, container, false)

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
                    .title("Current Location"))
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
        var arrayadap = ArrayAdapter(requireContext(), R.layout.dropdown_banks, banks[0])
        autocomplete.setAdapter(arrayadap)

        autocomplete = distanceChoice
        arrayadap = ArrayAdapter(requireContext(), R.layout.dropdown_distances, distances)
        autocomplete.setAdapter(arrayadap)

        view.findViewById<Button>(R.id.buttonSearch).setOnClickListener {
            val selectBank = bankChoice.text.toString()
            val selectDistance = distanceChoice.text.toString()

            fun isEmpty(): Boolean {
                var empty: Boolean = false
                if (selectBank == "Select Bank") {
                    view.findViewById<EditText>(R.id.editTextBank).hint =
                        "Please select a bank"
                    empty = true
                } else {
                    view.findViewById<EditText>(R.id.editTextBank).hint = ""
                }

                if (selectDistance == "Select Distance") {
                    view.findViewById<EditText>(R.id.editTextDistance).hint =
                        "Please select a distance"
                    empty = true
                } else {
                    view.findViewById<EditText>(R.id.editTextDistance).hint = ""
                }
                return empty
            }

            if (!isEmpty()) {
//                getATMAddress(selectBank, selectDistance.toInt() * 1000)
                getATMAddress(selectBank, selectDistance.toInt()*1000)
            }
//                getStreetAddress(userMarker!!.position)
        }
        return view
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
        val latLng = LatLng(location.latitude, location.longitude)
        Log.d("mytagmarker", userMarker?.position.toString())
        withContext(Dispatchers.Main) {
            userMarker?.position = latLng
            view.findViewById<TextView>(R.id.textViewCurrLocation).text = userMarker?.position.toString()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
//    private fun getStreetAddress(latLng: LatLng) {
//        var result = ""
//        var urlString = ""
//        var br: java.io.BufferedReader
//        var url: URL
//        GlobalScope.launch(Dispatchers.IO) {
//            try {
//                withContext(Dispatchers.Main) {
//                    urlString = "https://maps.googleapis.com/maps/api/geocode/json?" +
//                            "latlng=${userMarker!!.position.latitude},${userMarker!!.position.longitude}&" +
//                            "key=$apikey"
//                    url = URL(urlString)
//                }
//                val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
//                withContext(Dispatchers.Main) {
//                    connection.requestMethod = "GET"
//                    connection.doInput = true
//                    withContext(Dispatchers.IO) {
//                        connection.connect()
//                        br = connection.inputStream.bufferedReader()
//                        result = br.use { br.readText() }
//                    }
//                    connection.disconnect()
//                    val gson = GsonBuilder().create()
//                    val root = gson.fromJson(result, JsonObject::class.java)
////                    Log.d("mytagplaces", root.getAsJsonObject().get("results").asJsonArray[0].asJsonObject.get("formatted_address").asString)
//                }
//            } catch (e: Exception) {
//                Log.d("mytagplaceserror", e.toString())
//                e.printStackTrace()
//            }
//        }
////        for(result in getATMAddress(address, selectDistance.toInt()*1000)?.get("results")!!.asJsonArray) {
////            val index = banks[0].indexOf(selectBank)
////            val name = result.asJsonObject.get("name").asString
////            val location = result.asJsonObject.get("geometry").asJsonObject.get("location").asJsonObject
////            if(name.contains(banks[0][index]) || name.contains(banks[1][index])) {
////                GlobalScope.launch(Dispatchers.Main) {
////                    markerList.add(googleMap.addMarker(MarkerOptions().title(name).position(LatLng(location.get("lat").asDouble, location.get("lng").asDouble)))!!)
////                    Log.d("mytagplacesname", name)
////                    Log.d("mytagplaceslocation", location.toString())
////                }
////            }
////        }
//    }

    private fun getATMAddress(selectBank: String, radius: Int) {
        fun addAtm(result: JsonArray){
            if (result.size() > 0) {
                for(marker in markerList) {
                    marker.remove()
                }
                for (r in result) {
                    val index = banks[0].indexOf(selectBank)
                    val name = r.asJsonObject.get("name").asString
                    val location =
                        r.asJsonObject.get("geometry").asJsonObject.get("location").asJsonObject
                    if (name.contains(banks[0][index]) || name.contains(banks[1][index])) {
                        Log.d("mytagbank", index.toString() + name + location)
                        GlobalScope.launch(Dispatchers.Main) {
                            markerList.add(
                                googleMap.addMarker(
                                    MarkerOptions().title(name).position(
                                        LatLng(
                                            location.get("lat").asDouble,
                                            location.get("lng").asDouble
                                        )
                                    )
                                )!!
                            )
//                            Log.d("mytaggmarker", googleMap.addMarker(
//                                MarkerOptions().title(name).position(
//                                    LatLng(
//                                        location.get("lat").asDouble,
//                                        location.get("lng").asDouble
//                                    )
//                                )
//                            )!!::class.simpleName.toString()
//                            )
                        }
                    }
                }
                val builder = LatLngBounds.Builder()
                for (marker in markerList) {
                    builder.include(marker.position)
                }
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
            }
        }
        var result = ""
        var urlString = ""
        var br: java.io.BufferedReader
        var url: URL
        GlobalScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    urlString = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?" +
                            "location=${userMarker!!.position.latitude},${userMarker!!.position.longitude}&" +
                            "radius=$radius&" +
                            "type=atm&" +
                            "key=$apikey"
                    url = URL(urlString)
                }
                val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
                withContext(Dispatchers.Main) {
                    connection.requestMethod = "GET"
                    connection.doInput = true
                    withContext(Dispatchers.IO) {
                        connection.connect()
                        br = connection.inputStream.bufferedReader()
                        result = br.use { br.readText() }
                    }
                }
//            connection.connect()
                connection.disconnect()
                val gson = GsonBuilder().create()
                val root = gson.fromJson(result, JsonObject::class.java)
//                Log.d("mytagplaces", root.getAsJsonObject().get("results").asJsonArray.toString())
                withContext(Dispatchers.Main) {
                    addAtm(root.getAsJsonObject().get("results").asJsonArray)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("mytagplaceserror", e.toString())
            }
        }
    }
}