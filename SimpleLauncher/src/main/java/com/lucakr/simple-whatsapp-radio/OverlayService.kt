package com.lucakr.`simple-whatsapp-radio`

import android.app.Activity
import android.app.ActivityManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import ch.arnab.simplelauncher.R


class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private var activeOverlay: View? = null
    private var callerName:String? = null
    private lateinit var contactView: RecyclerView
    private var contactPos = 0
    private var notificationReceiverSemaphore = false

    override fun onCreate() {
        super.onCreate()
        Log.i("OverlayService", "Created")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Setup Broadcast Receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(whatsappNotificationListener, IntentFilter())

        setupDefaultList()

        state = WhatsAppState.CLOSED
        preloadOverlays()
        setOverlay(state)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /** UTILITIES **/
    private fun goHome() {
        startActivity(
                (Intent(Intent.ACTION_MAIN)).addCategory(Intent.CATEGORY_HOME)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** WHATSAPP INTERFACING **/
    enum class WhatsAppState {
        CLOSED, IN_CALL, INCOMING
    }
    private var state = WhatsAppState.CLOSED
    private lateinit var closedOverlay:View
    private lateinit var inCallOverlay:View
    private lateinit var incomingOverlay:View

    private val whatsappNotificationListener: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG,"RX $intent")
            callerName = intent.toString()
        }
    }

    private fun focusWhatsApp() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(FOCUS))
    }

    private fun videoCall(id: String)
    {
        // Setup whatsapp intent
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(
                Uri.parse("content://com.android.contacts/data/$id"),
                "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        )
        i.setPackage("com.whatsapp")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Can't get here without accepting the permission onCreate
        Log.i(TAG, "Starting WhatsApp VIDEO")
        startActivity(i)
    }

    private fun voipCall(id: String)
    {
        // Setup whatsapp intent
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(
                Uri.parse("content://com.android.contacts/data/$id"),
                "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        )
        i.setPackage("com.whatsapp")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Can't get here without accepting the permission onCreate
        Log.i(TAG, "Starting WhatsApp VOIP")
        startActivity(i)
    }

    /** OVERLAY MANAGEMENT **/

    private fun preloadOverlays() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // Closed Overlay
        closedOverlay = inflater.inflate(R.layout.main_overlay, null)
        closedOverlay.findViewById<Button>(R.id.call_button).setOnClickListener { callButtonPress() }
        closedOverlay.findViewById<Button>(R.id.left_button).setOnClickListener { scrollLeftButtonPress() }
        closedOverlay.findViewById<Button>(R.id.right_button).setOnClickListener { scrollRightButtonPress() }
        closedOverlay.findViewById<TextView>(R.id.topmost_view).setOnClickListener { middleClick() }
        closedOverlay.findViewById<RecyclerView>(R.id.name_list).adapter = ContactAdapter(whatsappContacts)
        closedOverlay.findViewById<RecyclerView>(R.id.name_list).suppressLayout(true)

        // Incoming Overlay
        incomingOverlay = inflater.inflate(R.layout.start_overlay, null)
        incomingOverlay.findViewById<Button>(R.id.decline_button).setOnClickListener{ declineButtonPress() }
        incomingOverlay.findViewById<Button>(R.id.answer_button).setOnClickListener{ answerButtonPress() }
        incomingOverlay.findViewById<TextView>(R.id.caller_name).text = getString(R.string.unknown_caller_name)
        incomingOverlay.findViewById<ImageView>(R.id.caller_image).setImageResource(android.R.color.transparent)

        // InCall Overlay
        activeOverlay!!.findViewById<Button>(R.id.end_button).setOnClickListener{ endButtonPress() }
        activeOverlay!!.findViewById<Button>(R.id.end_button_edge).setOnClickListener{ endButtonPress() }
    }

    private fun removeOverlay() {
        // Kill all overlays
        try { windowManager.removeView(closedOverlay) } catch(t: Throwable) {}
        try { windowManager.removeView(incomingOverlay) } catch(t: Throwable) {}
        try { windowManager.removeView(inCallOverlay) } catch(t: Throwable) {}
    }

    private fun setOverlay(currentState: WhatsAppState) {
        //return
        Log.i("OverlayService", "Setting overlay")

        // Custom listeners
        when(currentState) {
            WhatsAppState.CLOSED -> {
                closedOverlay.findViewById<RecyclerView>(R.id.name_list).scrollToPosition(contactPos)
            }

            WhatsAppState.INCOMING -> {
                try {
                    val foundContact = whatsappContacts.single { it.myDisplayName == callerName }
                    incomingOverlay.findViewById<TextView>(R.id.caller_name).text = callerName
                    if (foundContact.myThumbnail != "") {
                        incomingOverlay.findViewById<ImageView>(R.id.caller_image).setImageURI(foundContact.myThumbnail.toUri())
                    } else {
                        incomingOverlay.findViewById<ImageView>(R.id.caller_image).setImageResource(android.R.color.transparent)
                    }
                } catch (t: Throwable) {
                    incomingOverlay.findViewById<TextView>(R.id.caller_name).text = getString(R.string.unknown_caller_name)
                    incomingOverlay.findViewById<ImageView>(R.id.caller_image).setImageResource(android.R.color.transparent)
                }
            }

            else -> {}
        }

        // Remove all overlays
        removeOverlay()

        // Add the current overlay
        when(currentState) {
            WhatsAppState.CLOSED -> closedOverlay
            WhatsAppState.INCOMING -> incomingOverlay
            WhatsAppState.IN_CALL -> inCallOverlay
        }.let {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                //window.setDecorFitsSystemWindows(false)
//                it.windowInsetsController?.apply {
//                    hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
//                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//                }
//            } else {
            it.systemUiVisibility  =
                    View.SYSTEM_UI_FLAG_LOW_PROFILE or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            //}

            val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    0,
                    PixelFormat.TRANSLUCENT
            )
            windowManager.addView(it, params)
        }
    }

    /** UI CALLBACKS **/
    private var btnClickTime = System.currentTimeMillis()
    private val btnDebounceTime = 2000

    private var exitClickCount = 0
    private var exitFirstClickTime = System.currentTimeMillis()
    private val exitClickRequirement = 10
    private val exitClickTimeframe = 5000

    private fun declineButtonPress() {
        if(System.currentTimeMillis() - btnClickTime < btnDebounceTime) return
        btnClickTime = System.currentTimeMillis()

        Log.i(TAG, "Declining")
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DECLINE))
    }

    private fun answerButtonPress() {
        if(System.currentTimeMillis() - btnClickTime < btnDebounceTime) return
        btnClickTime = System.currentTimeMillis()

        Log.i(TAG,"Answering")
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ANSWER))
    }

    private fun endButtonPress() {
        if(System.currentTimeMillis() - btnClickTime < btnDebounceTime) return
        btnClickTime = System.currentTimeMillis()

        Log.i(TAG,"Ending")
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(END))
    }

    private fun middleClick() {
        if(System.currentTimeMillis() - exitFirstClickTime > exitClickTimeframe) {
            exitClickCount = 0
            exitFirstClickTime = System.currentTimeMillis()
        } else {
            exitClickCount++
            if(exitClickCount >= exitClickRequirement) {
                Log.i(TAG,"Exiting")
                removeOverlay()
                stopSelf()
            }
        }
    }

    private fun callButtonPress() {
        if(System.currentTimeMillis() - btnClickTime < btnDebounceTime) return
        btnClickTime = System.currentTimeMillis()

        if(whatsappContacts[contactPos].myVideoId != "")
        {
            videoCall(whatsappContacts[contactPos].myVideoId)
        }
        else if(whatsappContacts[contactPos].myVoipId != "")
        {
            voipCall(whatsappContacts[contactPos].myVoipId)
        }
    }

    private fun scrollLeftButtonPress() {
        if(contactPos > 0) {
            contactPos--
        } else if(contactPos == 0) contactPos = whatsappContacts.size-1

        contactView.suppressLayout(false)
        contactView.scrollToPosition(contactPos)
        contactView.suppressLayout(true)
    }

    private fun scrollRightButtonPress() {
        if(contactPos < whatsappContacts.size-1) {
            contactPos++
        } else if(contactPos == whatsappContacts.size-1) contactPos = 0

        contactView.suppressLayout(false)
        contactView.scrollToPosition(contactPos)
        contactView.suppressLayout(true)
    }

    /** WHATSAPP CONTACT LISTING **/

    class Contact(val id:Long, private val displayName:String) {
        var myId:Long = id
        var myDisplayName:String = displayName
        var myThumbnail:String = ""
        var myVoipId:String = ""
        var myVideoId:String = ""
    }

    private val whatsappContacts: MutableList<Contact> = mutableListOf()

    private val contactProjection: Array<out String> = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.PHOTO_URI
    )

    private fun setupDefaultList() {
        // Get contacts
        val cursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                contactProjection, null, null,
                ContactsContract.Contacts.DISPLAY_NAME)

        // Parse to find valid whatsapp contacts and add to secondary array
        while(cursor!!.moveToNext()) {
            val id:Long = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Data._ID))
            val displayName:String = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME))
            val mimeType:String =  cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE))
            val thumbnail = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.PHOTO_URI))

            if (mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.voip.call" || mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.video.call") {
                // Check if it exists in the list already
                var next: Contact? = whatsappContacts.find{ it.myDisplayName == displayName }

                // If not, add it in
                if(next == null) {
                    next = Contact(id, displayName)
                    if(thumbnail != null) next.myThumbnail = thumbnail
                    whatsappContacts.add(next)
                }

                // Get the index of the old or new entry
                val index = whatsappContacts.indexOf(next)

                // Update the relevant id
                if (mimeType == "vnd.android.cursor.item/vnd.com.whatsapp.voip.call") {
                    next.myVoipId = id.toString()
                }
                else{
                    next.myVideoId = id.toString()
                }

                // Correct the entry
                whatsappContacts[index] = next
            }

        }

        cursor.close()

        Log.i(TAG,whatsappContacts.toString())
    }

    class ContactAdapter(private val dataSource: MutableList<Contact>): RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        class ContactViewHolder(contactView: LinearLayout) : RecyclerView.ViewHolder(contactView) {
            val contactView: LinearLayout = contactView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
            val contactView = LayoutInflater.from(parent.context).inflate(R.layout.contact, parent, false) as LinearLayout

            return ContactViewHolder(contactView)
        }

        override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
            val titleTextView = holder.contactView.findViewById(R.id.contact_title) as TextView
            val thumbnailImageView = holder.contactView.findViewById(R.id.contact_thumbnail) as ImageView

            val curContact = dataSource[position]

            titleTextView.text = curContact.myDisplayName
            if(curContact.myThumbnail != "") {
                thumbnailImageView.setImageURI(curContact.myThumbnail.toUri())
            } else {
                thumbnailImageView.setImageResource(android.R.color.transparent)
            }
        }

        override fun getItemCount() = dataSource.size
    }

    /** COMPANIONS **/
    companion object {
        private const val TAG = "OverlayLog"

        const val FOCUS   = "call_focus"
        const val ANSWER  = "call_answer"
        const val DECLINE = "call_decline"
        const val END     = "call_end"
    }
}