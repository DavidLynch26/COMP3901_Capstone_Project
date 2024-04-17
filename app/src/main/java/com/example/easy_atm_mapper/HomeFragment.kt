package com.example.easy_atm_mapper

//import android.annotation.SuppressLint
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
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
import androidx.annotation.RequiresApi
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var userMarker: Marker? = null
    private lateinit var userAddress: Address
    private lateinit var markerList: MutableList<Marker>

//    private var fineLocPerm : String = android.Manifest.permission.ACCESS_FINE_LOCATION
//    private var coarseLocPerm : String = android.Manifest.permission.ACCESS_COARSE_LOCATION
    private val banks = listOf("Bank of Nova Scotia", "First Caribbean", "Sagicor", "Jamaica National", "National Commercial Bank", "JMMB", "CIBC")
    private val distances = listOf("1","2", "5", "10", "15", "20")

    private lateinit var locationManager: LocationManager
    private var isLocationEnabled: Boolean = false

    private lateinit var dialog: AlertDialog

    @OptIn(DelicateCoroutinesApi::class)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
                            20f
                        ), 2000, null
                    )
                }
            }
        }

        val bankChoice = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextViewBanks)
        val distanceChoice =
            view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextViewDistance)

        autocomplete = bankChoice
        var arrayadap = ArrayAdapter(requireContext(), R.layout.dropdown_banks, banks)
        autocomplete.setAdapter(arrayadap)

        autocomplete = distanceChoice
        arrayadap = ArrayAdapter(requireContext(), R.layout.dropdown_distances, distances)
        autocomplete.setAdapter(arrayadap)

        view.findViewById<Button>(R.id.buttonSearch).setOnClickListener {
            val selectBank = bankChoice.text.toString()
            val selectDistance = distanceChoice.text.toString()

            suspend fun isEmpty(): Boolean {
                var empty: Boolean = false
                if (selectBank == "Select Bank") {
                    withContext(Dispatchers.Main) {
                        view.findViewById<EditText>(R.id.editTextBank).hint =
                            "Please select a bank"
                    }
                    empty = true
                } else {
                    withContext(Dispatchers.Main) {
                        view.findViewById<EditText>(R.id.editTextBank).hint = ""
                    }
                }

                if (selectDistance == "Select Distance") {
                    withContext(Dispatchers.Main) {
                        view.findViewById<EditText>(R.id.editTextDistance).hint =
                            "Please select a distance"
                    }
                    empty = true
                } else {
                    withContext(Dispatchers.Main) {
                        view.findViewById<EditText>(R.id.editTextDistance).hint = ""
                    }
                }
                return empty
            }

            GlobalScope.launch(Dispatchers.Main) {
                if (!isEmpty()) {
                    getStreetAddress(userMarker!!.position)
                }
            }
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
        if (userMarker != null) {
            withContext(Dispatchers.Main) {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        userMarker!!.position,
                        15f
                    ), 2000, null
                )
            }
        }
        val latLng = LatLng(location.latitude, location.longitude)
        Log.d("mytagmarker", userMarker?.position.toString())
        withContext(Dispatchers.Main) {
            userMarker?.position = latLng
            view.findViewById<TextView>(R.id.textViewCurrLocation).text = userMarker?.position.toString()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getStreetAddress(latLng: LatLng) {
        Log.d("mytagmarker", latLng.toString())
        geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1, object: Geocoder.GeocodeListener{
            override fun onGeocode(addresses: MutableList<Address>) {
                if (addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val streetAddress = address.getAddressLine(0)
                    GlobalScope.launch(Dispatchers.Main) {
                        val currentAddress = userMarker?.position.toString() + streetAddress
                        Log.d("mytagmarker", currentAddress)
                    }
//                    view.findViewById<TextView>(R.id.textViewCurrLocation).text = currentAddress
                    userAddress = address
                }
            }

            override fun onError(errorMessage: String?) {
                Log.e("Geocoder", "Error: $errorMessage")
            }
        })
    }

    private fun getATMAddress(userAddress: Address) {

    }
}