// lib/providers/location_provider.dart - NEW
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';

class LocationProvider {
  // Request location permission
  Future<bool> requestLocationPermission() async {
    try {
      final status = await Permission.location.request();
      if (status.isGranted) {
        print('✅ Location permission granted');
        return true;
      } else {
        print('❌ Location permission denied');
        return false;
      }
    } catch (e) {
      print('❌ Error requesting location permission: $e');
      return false;
    }
  }

  // Get current location
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

      // Get position
      final position = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );

      print('✅ Location obtained: ${position.latitude}, ${position.longitude}');
      return position;
    } catch (e) {
      print('❌ Error getting location: $e');
      return null;
    }
  }

  // Format location for message
  String formatLocation(Position position) {
    return 'Location: ${position.latitude.toStringAsFixed(6)}, ${position.longitude.toStringAsFixed(6)}';
  }

  // Generate Google Maps link
  String generateMapsLink(Position position) {
    return 'https://www.google.com/maps?q=${position.latitude},${position.longitude}';
  }

  // Parse location from message
  Map<String, double>? parseLocation(String message) {
    try {
      final pattern = RegExp(r'Location: ([-\d.]+), ([-\d.]+)');
      final match = pattern.firstMatch(message);

      if (match != null) {
        return {
          'latitude': double.parse(match.group(1)!),
          'longitude': double.parse(match.group(2)!),
        };
      }
      return null;
    } catch (e) {
      print('❌ Error parsing location: $e');
      return null;
    }
  }
}
