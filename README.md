# SendSmsApp

An Android application for managing and monitoring GPS tracking devices via SMS communication. This app allows users to send diagnostic commands to GPS trackers, receive responses, and perform various checks on device configurations.

## Features

- **Device Management**: Load and manage a list of GPS devices from a CSV file
- **SMS Communication**: Send and receive SMS messages to/from GPS trackers
- **Diagnostic Tools**:
  - Check device IMEI and identification
  - Verify IP address and port configuration
  - Monitor GPS fix status
  - Validate protocol settings
  - Check heartbeat configuration (HC)
  - Monitor cornering settings
  - Time synchronization validation
- **Real-time Logging**: View command responses and diagnostic results in real-time
- **Compose UI**: Modern Android UI built with Jetpack Compose

## Requirements

- Android API level 21 (Android 5.0) or higher
- SMS permissions (SEND_SMS, RECEIVE_SMS, READ_SMS)
- Telephony feature (optional, for better compatibility)

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/SendSmsApp.git
   cd SendSmsApp
   ```

2. Open the project in Android Studio

3. Build and run the app on your device or emulator

## Usage

1. **Grant Permissions**: The app will request SMS permissions on first launch
2. **Load Devices**: Devices are automatically loaded from `app/src/main/res/raw/data.csv`
3. **Send Commands**:
   - Use the diagnostic buttons to send specific commands to devices
   - View responses in the log area
4. **Monitor Results**: Check the log for diagnostic information and device status

## Project Structure

- `app/src/main/java/com/example/sendsmsapp/`
  - `logic/`: Core business logic (GpsEngine, diagnostics)
  - `model/`: Data models (Device)
  - `sms/`: SMS handling (SmsReceiver, SmsBus)
  - `ui/`: User interface components
  - `util/`: Utilities (CSV loading)
- `app/src/main/res/raw/data.csv`: Device data file

## Technologies Used

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Build System**: Gradle with Kotlin DSL
- **Android SDK**: API 34 (compile), API 21 (minimum)
- **Dependencies**:
  - AndroidX Core KTX
  - AndroidX Lifecycle
  - AndroidX Compose BOM
  - Material 3

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Disclaimer

This app is designed for managing GPS tracking devices that communicate via SMS. Ensure you have proper authorization to send SMS commands to the devices you're managing. The app requires SMS permissions and should be used responsibly.</content>
<parameter name="filePath">c:\Users\omarb\OneDrive\Desktop\SendSmsApp\README.md
