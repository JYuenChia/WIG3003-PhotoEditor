# WIG3003-PhotoEditor
A JavaFX-based application designed for comprehensive photo management, digital image processing, and multimedia synthesis.

## Tech Stack
- **Java 17 & Maven**: Core application foundation and dependency management.
- **JavaFX 17 (Image & Canvas API)**: Powers the pixel-level manipulation for Digital Image Processing (DIP), Object Extraction, and Photo Mosaic Generation using `PixelReader` and `PixelWriter`.
- **Java 2D API / ImageIO**: Used for efficiently reading and writing image formats (PNG, JPG) to the local file system.
- **JavaCV (FFmpeg)**: Core library driving the Video Rendering & Editing engine for frame-by-frame video compilation.
- **Jakarta Mail**: Handles SMTP connections for the Direct Email Sharing functionality.

## Features
- **Digital Image Processing (DIP)**: 
  - Real-time brightness and contrast adjustment.
  - Grayscale conversion and dynamic custom borders (Polka Dots, Stripes, Gradient).
  - Magic Wand tool for selecting and highlighting similar colors.
  - Image resizing, pixel matrix rotation, and full Undo/Redo support.
- **Photo Mosaic Generation**: 
  - Create stunning mosaics using a custom Tile Library or Plain Color mode.
  - Configurable tile dimensions and blending opacities.
- **Object Extraction**: 
  - Interactive click-based color selection to isolate objects.
  - Adjustable color tolerance and alpha transparency.
  - Multiple output modes: Extract Only (transparent background), Extract with Background, and Color Mask.
- **Video Rendering & Editing**: 
  - Compile photos into videos with customizable settings (audio, text overlays, and transitions).
- **Direct Email Sharing**: 
  - Securely share your processed media directly from the application using SMTP.
- **Gallery Management**:
  - Built-in gallery to seamlessly save and manage processed images.
- **Annotation & Metadata Management**:
  - Append custom notes and descriptions to individual images.
  - Real-time UI synchronization via visual indicators (Heart ♥ icon) in the gallery.

## Setup & Configuration

### Prerequisites
- Java 17 or higher
- Maven

### Running the Application
1. Clone the repository:
   ```bash
   git clone https://github.com/JYuenChia/WIG3003-PhotoEditor.git
   ```
2. Navigate to the project directory:
   ```bash
   cd WIG3003-PhotoEditor
   ```
3. Run the application using the Maven wrapper:
   ```bash
   ./mvnw clean javafx:run
   ```
   *(On Windows, you can use `mvnw.cmd clean javafx:run`)*

### Email Configuration (Optional for Sharing)
To enable the email sharing feature, you need to configure the SMTP settings:

Create a file named `email_configuration.example` in the project root and fill in your own sender email and app password.

Put in the following environment variables:
- `SENDER_EMAIL`
- `APP_PASSWORD`
- `SMTP_HOST`
- `SMTP_PORT`
