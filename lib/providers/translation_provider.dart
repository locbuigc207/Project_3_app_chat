// lib/providers/translation_provider.dart - NEW
import 'package:translator/translator.dart';

class TranslationProvider {
  final GoogleTranslator _translator = GoogleTranslator();

  Future<String?> translateText({
    required String text,
    required String targetLanguage,
    String sourceLanguage = 'auto',
  }) async {
    try {
      print('🌐 Translating to $targetLanguage');

      // Validate input
      if (text.trim().isEmpty) {
        return null;
      }

      final translation = await _translator
          .translate(
        text,
        from: sourceLanguage,
        to: targetLanguage,
      )
          .timeout(
        const Duration(seconds: 10),
        onTimeout: () {
          throw Exception('Translation timeout');
        },
      );

      print('✅ Translation: ${translation.text}');
      return translation.text;
    } catch (e) {
      print('❌ Translation error: $e');
      // Return original text if translation fails
      return text;
    }
  }

  // Detect language
  Future<String?> detectLanguage(String text) async {
    try {
      final detection = await _translator.translate(text, from: 'auto');
      print('✅ Detected language: ${detection.sourceLanguage}');
      return detection.sourceLanguage.toString();
    } catch (e) {
      print('❌ Language detection error: $e');
      return null;
    }
  }

  // Popular language codes
  static const Map<String, String> languages = {
    'en': 'English',
    'vi': 'Tiếng Việt',
    'zh-cn': '中文 (简体)',
    'zh-tw': '中文 (繁體)',
    'ja': '日本語',
    'ko': '한국어',
    'es': 'Español',
    'fr': 'Français',
    'de': 'Deutsch',
    'ru': 'Русский',
    'ar': 'العربية',
    'hi': 'हिन्दी',
    'pt': 'Português',
    'it': 'Italiano',
    'th': 'ไทย',
  };

  // Get language name
  String getLanguageName(String code) {
    return languages[code] ?? code.toUpperCase();
  }
}
