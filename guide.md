# Self-Hosting Guide: Rembg AI Background Removal with Cloudflare Tunnel

This guide walks you through setting up a dedicated, ultra-high-resolution AI background removal server on your computer or cloud server (Linux, Windows, macOS) and securely connecting it to **PixelEditor** on your Android device for free using **Cloudflare Tunnel** (no public IP or port forwarding required).

---

## Architecture Overview

```
[ Android Device (PixelEditor) ] 
               │
      HTTPS API Request
               ▼
[ Cloudflare Edge Tunnel ]
               │
    Secure Outbound Tunnel
               ▼
[ cloudflared Daemon on your PC ]
               │
          localhost:8000
               ▼
[ FastAPI + Rembg / U2-Net / RMBG-1.4 ]
```

---

## Method 1: Python FastAPI Server (Recommended for Windows / Mac / Linux)

### Step 1: Install Python Requirements
Make sure you have Python 3.9+ installed, then run:

```bash
pip install fastapi uvicorn rembg onnxruntime python-multipart
```

> **Tip (GPU Acceleration)**:
> If you have an NVIDIA GPU with CUDA, install `onnxruntime-gpu` for ultra-fast 50ms inference:
> ```bash
> pip install onnxruntime-gpu
> ```

---

### Step 2: Create `server.py`
Create a file named `server.py` and paste the following code:

```python
from fastapi import FastAPI, UploadFile, File, Response, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from rembg import remove, new_session
import uvicorn
import io

app = FastAPI(title="PixelEditor Rembg Server")

# Allow requests from any client
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize neural AI model session (Choose your preferred model)
# Available models: "u2net" (default), "bria-rmbg" (studio quality), "isnet-general-use", "silueta" (fast)
print("Loading background removal AI model...")
session = new_session("u2net")
print("Model loaded successfully!")

@app.get("/")
def health_check():
    return {"status": "ok", "service": "PixelEditor Rembg API"}

@app.post("/remove")
async def remove_background(file: UploadFile = File(...)):
    try:
        input_bytes = await file.read()
        if not input_bytes:
            raise HTTPException(status_code=400, detail="Empty image file received")
        
        # Run AI cutout
        output_bytes = remove(input_bytes, session=session)
        
        return Response(content=output_bytes, media_type="image/png")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
```

---

### Step 3: Start the Python Server
Run the server:
```bash
python server.py
```
You should see:
```
INFO:     Started server process
INFO:     Waiting for application startup.
Loading background removal AI model...
Model loaded successfully!
INFO:     Application startup complete.
INFO:     Uvicorn running on http://0.0.0.0:8000
```

---

## Method 2: Docker Container (One-Liner)

If you have Docker installed, you can launch the Rembg HTTP server with a single command:

```bash
docker run -d --name rembg-server -p 8000:7000 danielgatis/rembg s
```

---

## Expose Server Securely using Cloudflare Tunnel (`cloudflared`)

Cloudflare Tunnel creates an encrypted, high-speed public HTTPS URL for your local server without opening firewall ports.

### Step 1: Download `cloudflared`
- **Windows**: Download `cloudflared.exe` from [Cloudflare Releases](https://github.com/cloudflare/cloudflared/releases) or via winget:
  ```powershell
  winget install --id Cloudflare.cloudflared
  ```
- **macOS**:
  ```bash
  brew install cloudflared
  ```
- **Linux (Ubuntu/Debian)**:
  ```bash
  curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
  sudo dpkg -i cloudflared.deb
  ```

---

### Step 2: Start the Free Tunnel
Run the following command in your terminal while `server.py` is running:

```bash
cloudflared tunnel --url http://localhost:8000
```

Cloudflare will generate a public HTTPS URL looking like:
```
+--------------------------------------------------------------------------------------------+
|  Your quick Tunnel has been created! Visit it at (it may take some time to be reachable):  |
|  https://xxxx-xxxx-xxxx.trycloudflare.com                                                  |
+--------------------------------------------------------------------------------------------+
```

Your API endpoint will be:
`https://xxxx-xxxx-xxxx.trycloudflare.com/remove`

---

## Step 4: Configure Inside PixelEditor

1. Open **PixelEditor** on your Android device.
2. Select any Photo layer or tap **Remove Background**.
3. Tap the **Settings (⚙️)** icon in the top right of the Background Remover screen.
4. Select **Custom Server (Rembg)**.
5. In the **Server URL** field, paste your Cloudflare URL:
   ```
   https://xxxx-xxxx-xxxx.trycloudflare.com/remove
   ```
6. Tap **Save Settings**.
7. Tap **🌐 Cloud AI** to process cutouts through your self-hosted server!

---

## Offline Model Note

PixelEditor also includes the **🧠 Local AI (U²-Net)** engine (`u2netp.onnx`) directly on the device, allowing you to remove backgrounds 100% offline with zero setup required. Use your self-hosted server whenever you want higher-resolution models (like BRIA RMBG 1.4 or full U²-Net 170MB).
