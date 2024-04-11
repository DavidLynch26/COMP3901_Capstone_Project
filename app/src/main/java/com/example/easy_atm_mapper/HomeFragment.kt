package com.example.easy_atm_mapper

//import android.annotation.SuppressLint
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var view : View
    private lateinit var mapFragment : SupportMapFragment
    private lateinit var googleMap : GoogleMap
    private var fineLocPerm : String = android.Manifest.permission.ACCESS_FINE_LOCATION
    private var coarseLocPerm : String = android.Manifest.permission.ACCESS_COARSE_LOCATION
    private val banks = listOf("Bank of Nova Scotia", "First Caribbean", "Sagicor", "Jamaica National", "National Commercial Bank", "JMMB", "CIBC")
    private val distances = listOf("1","2", "5", "10", "15", "20")
    private lateinit var autocomplete : AutoCompleteTextView
    private lateinit var fusedLocationProviderClient : FusedLocationProviderClient

    private val locationRequest = LocationRequest.Builder(102, 5000).build()
    private lateinit var locationCallback: LocationCallback

    private lateinit var geocoder: Geocoder

    private var userMarker = MarkerOptions()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_home, container, false)

        geocoder = Geocoder(requireContext(), Locale.getDefault())
        userMarker.title("User Location")

        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync {
            googleMap = it
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if(checkPerms()){
            fusedLocationProviderClient.lastLocation.addOnSuccessListener {
                location: Location? ->
                if (location != null) {
                    userMarker.position(
                        LatLng(
                            location.latitude,
                            location.longitude
                        )
                    )
                    googleMap.addMarker(userMarker)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userMarker.position, 15f))
                    updateUI(location)
                }
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                updateUI(p0.lastLocation)
            }
        }

        view.findViewById<ImageButton>(R.id.imageButtonRecentre).setOnClickListener {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userMarker.position, 20f), 2000, null)
        }

        val bankChoice = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextViewBanks)
        val distanceChoice = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextViewDistance)

        autocomplete = bankChoice
        var arrayadap = ArrayAdapter(requireContext(), R.layout.dropdown_banks, banks)
        autocomplete.setAdapter(arrayadap)

        autocomplete = distanceChoice
        arrayadap = ArrayAdapter(requireContext(), R.layout.dropdown_distances, distances)
        autocomplete.setAdapter(arrayadap)

        view.findViewById<Button>(R.id.buttonSearch).setOnClickListener{
//            val selectBank = bankChoice.text.toString()
//            val selectDistance = distanceChoice.text.toString()

            geocoder.getFromLocation(userMarker.position.latitude, userMarker.position.longitude, 10, object: Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    Log.d("mytaggeo", addresses.size.toString())
                    for (address in addresses) {
                        Log.d("mytaguseraddr", address.toString())
                    }
                }

                override fun onError(errorMessage: String?) {
                    super.onError(errorMessage)
                    Log.d("mytaggeo", errorMessage.toString())
                }
            })

            geocoder.getFromLocationName("48 cornwall avenue jamaica", 10, object: Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    for (address in addresses) {
                        val latLng = LatLng(address.latitude, address.longitude)
                        Log.d("mytagbankaddr", latLng.toString())
                        googleMap.addMarker(MarkerOptions().position(latLng).title(address.getAddressLine(0)))
//                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        Log.d("mytagbankaddr", address.getAddressLine(0))
                    }
                }

                override fun onError(errorMessage: String?) {
                    super.onError(errorMessage)
                    Log.d("mytaggeo", errorMessage.toString())
                }
            })
        }
        return view
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
        if (checkPerms())
            startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun checkPerms(): Boolean{
        if (ActivityCompat.checkSelfPermission(requireContext(), fineLocPerm) == PackageManager.PERMISSION_GRANTED) {
            return true
        } else {
            shouldShowRequestPermissionRationale(fineLocPerm)
            return false
        }
    }

    private fun updateUI(location: Location?) {
        if (location != null) {
            Log.d("mytag", location.toString())
            val latLng = LatLng(location.latitude, location.longitude)
            userMarker.position(latLng)
            view.findViewById<TextView>(R.id.textViewCurrLocation).text =
                location?.latitude.toString() + location?.longitude.toString()
        }
    }
}