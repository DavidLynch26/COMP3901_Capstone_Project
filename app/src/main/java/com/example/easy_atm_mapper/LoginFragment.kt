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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class LoginFragment : Fragment() {

    var exists: Boolean = false
    private lateinit var view : View

    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_login, container, false)

        view.findViewById<TextView>(R.id.textViewSignup).setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.action_loginFragment_to_signupFragment)
        }

        view.findViewById<Button>(R.id.buttonLogin).setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                login()
            }
        }

//        Log.d("mytag", context?.filesDir.toString())
//
//        if(File(context?.filesDir, "user/user.txt").exists()){
//            val user = readUserFile(context?.filesDir.toString() + "/user/user.txt")
//
//            isUser(user.first, user.second)
//        }
        return view
    }

//    @OptIn(DelicateCoroutinesApi::class)
    @OptIn(DelicateCoroutinesApi::class)
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

        if (empty) {
            isUser(username.text.toString().trim(), password.text.toString().trim())
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun isUser(username: String, password: String) {
        val pb = (context as MainActivity)
            .findViewById<ProgressBar>(R.id.progressBar)
        pb.visibility = View.VISIBLE
        var found : Boolean = false
        GlobalScope.launch (Dispatchers.IO) {
            usersCollection.get()
                .addOnSuccessListener { querySnapshot ->
                    for (document in querySnapshot.documents) {
                        val user = document.toObject<User>()
                        if (user != null) {
                            if (user.username == username &&
                                user.password == password) {
                                makeUserFile(username, password)
                                Toast.makeText(
                                    context,
                                    "User Login Successfully",
                                    Toast.LENGTH_SHORT
                                ).show()

                                Log.d("mytag", "User Login Successfully")
                                Navigation.findNavController(view)
                                    .navigate(R.id.action_loginFragment_to_homeFragment)
                                pb.visibility = View.GONE
                                found = true
                            }
                            break
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
    }

    private fun readUserFile(userFile: String): Pair<String, String> {
        val file = File(userFile)

        val (username, password) = file.readText().split("_")
        val user: Pair<String, String> = Pair(username, password)

        Log.d("mytag", user.toString())

        return user
    }

    private fun makeUserFile(username: String, password: String) {
        val file = File(context?.filesDir, "user")
        file.createNewFile()
        file.writeText("$username+_+$password")
    }
}