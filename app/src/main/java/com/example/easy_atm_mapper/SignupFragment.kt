package com.example.easy_atm_mapper

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.navigation.Navigation
import com.example.easy_atm_mapper.models.User
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.*

class SignupFragment : Fragment() {

    private val db = Firebase.firestore
    private val usersCollection = db.collection("users")
    private lateinit var view : View

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_signup, container, false)

        view.findViewById<ImageButton>(R.id.imageButtonBack).setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.action_signupFragment_to_loginFragment)
        }

        view.findViewById<Button>(R.id.buttonCreateAccount).setOnClickListener {
            saveData()
        }
        return view
    }

    private fun saveData(){
        val firstname = view.findViewById<EditText>(R.id.editTextFirstname)
        val lastname = view.findViewById<EditText>(R.id.editTextLastname)
        val username = view.findViewById<EditText>(R.id.editTextUsername)
        val email = view.findViewById<EditText>(R.id.editTextEmailAddress)
        val password = view.findViewById<EditText>(R.id.editTextPassword)
        val emailPattern = Regex ("[a-zA-Z\\d._-]+@[a-z]+\\.+[a-z]+")

        fun isEmpty() : Boolean{
            var empty : Boolean = false
            if(firstname.text.toString().trim().isEmpty()) {
                firstname.error = "Enter Your First Name"
                empty = true
            }
            if(lastname.text.toString().trim().isEmpty()) {
                lastname.error = "Enter Your Last Name"
                empty = true
            }
            if(username.text.toString().trim().isEmpty()) {
                username.error = "Enter Your Username"
                empty = true
            }
            if(email.text.toString().trim().isEmpty()) {
                email.error = "Enter Your Email Address"
                empty = true
            }else if(!email.text.toString().trim().matches(emailPattern)){
                email.error = "Not a valid Email Address"
                empty = true
            }
            if(password.text.toString().trim().isEmpty()) {
                password.error = "Enter Your Password"
                empty = true
            }
            return empty
        }

        val save : Boolean = !isEmpty()

        if (save){
            var avail : Boolean = true
            val pb = (context as MainActivity)
                .findViewById<ProgressBar>(R.id.progressBar)
            pb.visibility = View.VISIBLE

            val user = User(firstname.text.toString().trim(), lastname.text.toString().trim(), username.text.toString().trim(), email.text.toString().trim(), password.text.toString().trim())

            fun make(){
                if (avail){
                    usersCollection.document(user.id!!)
                        .set(user)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Successfully Added User", Toast.LENGTH_SHORT)
                                .show()
                            Navigation.findNavController(view).navigate(R.id.action_signupFragment_to_loginFragment)
                            pb.visibility = View.GONE
                        }
                        .addOnFailureListener {exception ->
                            Toast.makeText(context, "Error: ${exception.message}", Toast.LENGTH_SHORT)
                                .show()
                            pb.visibility = View.GONE
                        }
                }
            }

            usersCollection.get()
                .addOnSuccessListener {querySnapshot ->
                    for (document in querySnapshot.documents) {
                        val found = document.toObject<User>()
                        if (found != null) {
                            if (found.username == username.text.toString().trim()) {
                                avail = false
                                username.error = "Username is taken"
                            }
                            if (found.email == email.text.toString().trim()){
                                avail = false
                                email.error = "Email is taken"
                            }
                        }
                        if (!avail) {
                            pb.visibility = View.GONE
                            break
                        }
                    }
                    if (avail) make()
                }
                .addOnFailureListener {exception ->
                    Toast.makeText(context, "Error: ${exception.message}", Toast.LENGTH_SHORT)
                        .show()
                    pb.visibility = View.GONE
                }
        }
    }
}