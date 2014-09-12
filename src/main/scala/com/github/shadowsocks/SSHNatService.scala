/*
 * TunnelAr - A shadowsocks/ssh client for Android
 * Copyright (C) 2014 <gaspar87@gmail.com> <gdelca5@gmail.com>
 *
 * based on Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2014 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package com.github.tunnelar
 
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content._
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os._
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.analytics.tracking.android.{Fields, MapBuilder, EasyTracker}
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import org.apache.http.conn.util.InetAddressUtils
import scala.collection._
import java.util.{TimerTask, Timer}
import android.net.TrafficStats
import scala.concurrent.ops._
import com.github.shadowsocks.utils._
import scala.Some
import android.graphics.Color
import com.github.shadowsocks.aidl.Config
import scala.collection.mutable.ArrayBuffer
import java.io.File
import com.github.shadowsocks.dpfwds._
import com.github.shadowsocks._

class SSHNatService extends NatService {

  var dpfwds: Option[DPfwdS] = None
  
  def startSSHDaemon() {
    fillACLList()
    
	dpfwds.map{ _dpfwds =>
              _dpfwds.terminate
              dpfwds = None
	    } orElse {
              val _dpfwds = new DPfwdS("127.0.0.1",
                                      config.localPort,"tunnelar", config.proxy, config.remotePort, 
                                      passwd = Some(config.sitekey.mkString))
  
              _dpfwds.start
              dpfwds = Some(_dpfwds)
              dpfwds
	    }	
  }
  
  /** Called when the activity is first created. */
  override def handleConnection: Boolean = {

    startSSHDaemon()
    startRedsocksDaemon()
    setupIptables
    flushDNS()

    true
  }
  
  override def killProcesses() {
    Console.runRootCommand(Utils.getIptables + " -t nat -F OUTPUT")

    val ab = new ArrayBuffer[String]

    ab.append("kill -9 `cat " + Path.BASE +"redsocks.pid`")
    ab.append("killall -9 redsocks")
    ab.append("kill -15 `cat " + Path.BASE + "pdnsd.pid`")
    ab.append("killall -15 pdnsd")

    Console.runRootCommand(ab.toArray)
	
	dpfwds.map{ _dpfwds =>
              _dpfwds.terminate
              dpfwds = None
	}
  }

}