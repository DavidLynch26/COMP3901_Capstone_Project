package com.example.easy_atm_mapper

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.Navigation
import com.example.easy_atm_mapper.models.User
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.database.getValue
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date

class LoginFragment : Fragment() {

    private lateinit var view : View
    var exists: Boolean = false
    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_login, container, false)

        view.findViewById<TextView>(R.id.textViewSignup).setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.action_loginFragment_to_signupFragment)
        }

        view.findViewById<Button>(R.id.buttonLogin).setOnClickListener {
            login()
        }
        return view
    }

    private fun login(){
        val username = view.findViewById<EditText>(R.id.editTextUsernameL)
        val password = view.findViewById<EditText>(R.id.editTextPasswordL)

        fun isEmpty(): Boolean {
            var empty: Boolean = false
            if (username.text.toString().trim().isEmpty()) {
                username.error = "Enter Your Username"
                empty = true
            }

            if (password.text.toString().trim().isEmpty()) {
                password.error = "Enter Your Password"
                empty = true
            }
            return empty
        }

        val empty: Boolean = !isEmpty()

        fun isUser() {
            val pb = (context as MainActivity)
                .findViewById<ProgressBar>(R.id.progressBar)
            pb.visibility = View.VISIBLE
            var found : Boolean = false
            usersCollection.get()
                .addOnSuccessListener { querySnapshot ->
                    for (document in querySnapshot.documents) {
                        val user = document.toObject<User>()
                        if (user != null) {
                            if (user.username == username.text.toString().trim() &&
                                user.password == password.text.toString().trim()) {
                                Toast.makeText(
                                    context,
                                    "User Login Successfully",
                                    Toast.LENGTH_SHORT)
                                    .show()
                                Navigation.findNavController(view).navigate(R.id.action_loginFragment_to_homeFragment)
                                pb.visibility = View.GONE
                                found = true
                                break
                            }
                        }
                    }
                    if (!found){
                        Toast.makeText(
                            context,
                            "User Not Found",
                            Toast.LENGTH_SHORT)
                            .show()
                        pb.visibility = View.GONE
                    }
                }
                .addOnFailureListener {exception ->
                    Toast.makeText(context, "Error: ${exception.message}", Toast.LENGTH_SHORT)
                        .show()
                    pb.visibility = View.GONE
                }
        }

        if (empty) {
            isUser()
        }
    }
}