// lib/providers/location_provider.dart - COMPLETE FIXED
import 'package:geolocator/geolocator.dart';

class LocationProvider {
  /// Request location permission
  Future<bool> requestLocationPermission() async {
    try {
      // Check if location services are enabled
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        print('❌ Location services are disabled');
        // Try to open location settings
        await Geolocator.openLocationSettings();
        return false;
      }

      // Check current permission status
      LocationPermission permission = await Geolocator.checkPermission();

      if (permission == LocationPermission.denied) {
        // Request permission
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          print('❌ Location permission denied');
          return false;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        print('❌ Location permission permanently denied');
        // Open app settings to allow user to enable
        await Geolocator.openAppSettings();
        return false;
      }

      print('✅ Location permission granted');
      return true;
    } catch (e) {
      print('❌ Error requesting location permission: $e');
      return false;
    }
  }

  /// Check if location permission is granted
  Future<bool> hasLocationPermission() async {
    try {
      final permission = await Geolocator.checkPermission();
      return permission == LocationPermission.always ||
          permission == LocationPermission.whileInUse;
    } catch (e) {
      print('❌ Error checking permission: $e');
      return false;
    }
  }

  /// Get current location
  Future<Position?> getCurrentLocation() async {
    try {
      // Check if location service is enabled
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        print('❌ Location services are disabled');
        return null;
      }

      // Check permission
      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          print('❌ Location permissions are denied');
          return null;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        print('❌ Location permissions are permanently denied');
        return null;
      }

      // Get position with timeout
      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 15),
        ),
      );

      print('✅ Location obtained: ${position.latitude}, ${position.longitude}');
      return position;
    } catch (e) {
      print('❌ Error getting location: $e');
      return null;
    }
  }

  /// Get last known location (faster, might be less accurate)
  Future<Position?> getLastKnownLocation() async {
    try {
      final position = await Geolocator.getLastKnownPosition();
      if (position != null) {
        print(
            '✅ Last known location: ${position.latitude}, ${position.longitude}');
      }
      return position;
    } catch (e) {
      print('❌ Error getting last known location: $e');
      return null;
    }
  }

  /// Format location for message
  String formatLocation(Position position) {
    return '📍 Location: ${position.latitude.toStringAsFixed(6)}, ${position.longitude.toStringAsFixed(6)}';
  }

  /// Generate Google Maps link
  String generateMapsLink(Position position) {
    return 'https://www.google.com/maps?q=${position.latitude},${position.longitude}';
  }

  /// Generate Apple Maps link
  String generateAppleMapsLink(Position position) {
    return 'https://maps.apple.com/?q=${position.latitude},${position.longitude}';
  }

  /// Generate full location message with link
  String generateLocationMessage(Position position) {
    final locationText = formatLocation(position);
    final mapsLink = generateMapsLink(position);
    return '$locationText\n$mapsLink';
  }

  /// Parse location from message
  Map<String, double>? parseLocation(String message) {
    try {
      // Pattern: Location: lat, lng or 📍 Location: lat, lng
      final patterns = [
        RegExp(r'Location:\s*([-\d.]+),\s*([-\d.]+)'),
        RegExp(r'📍\s*Location:\s*([-\d.]+),\s*([-\d.]+)'),
        RegExp(r'([-\d.]+),\s*([-\d.]+)'), // Simple lat,lng format
      ];

      for (final pattern in patterns) {
        final match = pattern.firstMatch(message);
        if (match != null && match.groupCount >= 2) {
          final lat = double.tryParse(match.group(1)!);
          final lng = double.tryParse(match.group(2)!);

          if (lat != null && lng != null) {
            // Validate coordinates
            if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
              return {
                'latitude': lat,
                'longitude': lng,
              };
            }
          }
        }
      }
      return null;
    } catch (e) {
      print('❌ Error parsing location: $e');
      return null;
    }
  }

  /// Check if message contains location
  bool isLocationMessage(String message) {
    return parseLocation(message) != null ||
        message.contains('google.com/maps') ||
        message.contains('maps.apple.com');
  }

  /// Calculate distance between two positions
  double calculateDistance(Position start, Position end) {
    return Geolocator.distanceBetween(
      start.latitude,
      start.longitude,
      end.latitude,
      end.longitude,
    );
  }

  /// Format distance to human readable string
  String formatDistance(double meters) {
    if (meters < 1000) {
      return '${meters.toStringAsFixed(0)} m';
    } else {
      return '${(meters / 1000).toStringAsFixed(1)} km';
    }
  }

  /// Get address from coordinates (reverse geocoding)
  /// Note: This requires additional setup with a geocoding service
  Future<String?> getAddressFromCoordinates(Position position) async {
    // For full address lookup, you would integrate with:
    // - Google Geocoding API
    // - OpenStreetMap Nominatim
    // - Mapbox Geocoding
    // For now, return formatted coordinates
    return 'Lat: ${position.latitude.toStringAsFixed(4)}, Lng: ${position.longitude.toStringAsFixed(4)}';
  }

  /// Stream location updates
  Stream<Position> getLocationStream({
    int distanceFilter = 10,
    LocationAccuracy accuracy = LocationAccuracy.high,
  }) {
    return Geolocator.getPositionStream(
      locationSettings: LocationSettings(
        accuracy: accuracy,
        distanceFilter: distanceFilter,
      ),
    );
  }
}
