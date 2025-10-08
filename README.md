# USSOI CAMfeed 

- Note: API requests are not authenticated.` yet to implement `

> **⚠️ Important Note:**
> *  See https://github.com/nikipoatgit/ussoi_camfeed for Android Client  
> *  See https://github.com/nikipoatgit/GCS_For_USSOI for Host Implementation

#### Future Updates 
* ability to add turn server and self host one on (tcp only if tunnel don't support udp )
* Basic authentication 
* api access based on role  
* MSE + Websocket (this will take time as i have to build it from scratch )


## 📑 Table of Contents

- [System Architecture](#-system-architecture)
- [Key Features](#-key-features)
- [API Endpoints](#-api-endpoints)
  - [Streaming API](#1-streaming-api-wsipstreaming)
  - [Control API](#2-control-api-wsipcontrolapi)
  - [Telemetry API](#3-telemetry-api-wsiptelemetry)
- [Getting Started](#-getting-started)
---

##  System Architecture

![USSOI CAMfeed Flowchart](doc/ussoiFlowchart.jpg)


## APP
* **Background Operation:** The service is designed to run persistently in the background, even when the screen is off.
* The application initiates a connection request over HTTP/HTTPS and then upgrades the protocol to WebSocket (WS/WSS).
* AppCN first connects to ``ws://<ip>/control/api`` based on the selected preference it then establishes subsequent connections to the service endpoints (HTTPS or WS/WSS) — therefore the host must be listening on all listed endpoints .

##  API Endpoints

The system connects three  WebSocket endpoints (Assuming Host is listening on it )for different functionalities.

### 1. Streaming API: `ws://<ip>/streaming`

This endpoint handles the WebRTC negotiation for establishing the video and audio stream handled by `WebRtcHandler.java`.

#### DATA  FORMAT 

1.  Upon a client's successful connection, the client sends a status (connected / not connected) update.

    ```json
    {
      "type": "getStatus",
      "status": "<status>"
    }
    ```

2.  The client then immediately sends its **ICE candidates** and **SDP offer** to initiate the WebRTC session.

    * **ICE Candidate Example:**
        ```json
        {
          "type": "ice-candidate",
          "candidate": {
            "candidate": "<candidate>",
            "sdpMid": "0",
            "sdpMLineIndex": 0
          }
        }
        ```
    * **SDP Offer Example:**
        ```json
        {
          "type": "offer",
          "sdp": "<sdp>"
        }
        ```

3.  The host must respond with its own **SDP answer** and **ICE candidates** to complete the handshake.

    * **SDP Answer:**
        ```json
        {
          "type": "answer",
          "sdp": "<your-sdp-answer>"
        }
        ```
    * **ICE Candidate:**
        ```json
        {
          "type": "ice-candidate",
          "candidate": "<your-candidate>",
          "sdpMid": "0",
          "sdpMLineIndex": 0
        }
        ```

#### Dynamic Configuration

You can configure the video stream on-the-fly by sending a `config` message.

```json
{
  "type": "config",
  "videoparams": {
    "width": 640,
    "height": 480,
    "fps": 15
  },
  "video": true,
  "audio": true,
  "bitrate": 1000
}
```
## 2. Control API : `ws://<ip>/control/api`

### 🎥 Streaming Controls
All actions here are processed by `ServiceManager.java`.
1. This allows for starting and stopping the video stream.

    * **Stop Streaming**
        ```json
        {
        "sub_type": "stream",
        "control": "stop"
        }
        ```

    * **Start / Restart Streaming**
        ```json
        {
        "sub_type": "stream",
        "control": "restart"
        }
        ```

2.  This is used to manage the Mavlink communication link over UART (USB/BT).

    * **Stop Mavlink Communication**
        ```json
        {
        "sub_type": "mavlink",
        "control": "stop"
        }
        ```

    * **Start / Restart Mavlink Communication**
        ```json
        {
        "sub_type": "mavlink",
        "control": "restart"
        }
        ```
> **⚠️ Important Notes on Mavlink:**
>
> * If Mavlink was disabled before the service started, user permission is required when connecting a USB or Bluetooth device for the first time.
> * Physical reconnection of a USB device will always require the user to 
>  grant permission again.

---

###  Android Device Status

Request a  status report from the Android device.

* **Request Status**
    Send the following JSON to query the device:
    ```json
    {
      "sub_type": "client"
    }
    ```

* **Sample Response**
    You will receive a JSON object containing key device metrics:
    ```json
    {
      "nameValuePairs": {
        "bat%": 35,
        "batTemp": 36.5,
        "batAmp": 1.2,
        "signalDbm": -65,
        "sim": "jio",
        "netTyp": 13,
        "upMBps": 0.55,
        "dwnMBps": 2.34,
        "totalMb": 125.6,
        "latitude": 45.0760,
        "longitude": 24.8777,
        "altitude": 23.3,
        "accuracy": 5.0,
        "speed": 0.8
      }
    }
    ```


### 3. Telemetry API: `ws://<ip>/telemetry`
* This endpoint provides a raw, bi-directional communication channel for UART/serial data. Any data sent or received through this WebSocket is forwarded directly to/from the connected hardware. Data is transmitted as a raw byte stream.
---
## Get Started 


### end of file 