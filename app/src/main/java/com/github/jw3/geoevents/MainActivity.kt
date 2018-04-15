package com.github.jw3.geoevents

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val map = ArcGISMap(Basemap.Type.TOPOGRAPHIC, 34.056295, -117.195800, 16)

        mapView.map = map

        val ld = mapView.locationDisplay
        ld.autoPanMode = LocationDisplay.AutoPanMode.NAVIGATION
        if (!ld.isStarted)
            ld.startAsync()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }
}
