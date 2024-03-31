package com.example.easy_atm_mapper

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

//        view.findViewById<Button>(R.id.buttonLogin).setOnClickListener {
//            Navigation.findNavController(view).navigate(R.id.action_loginFragment_to_signupFragment)
//        }

        return view
    }
}