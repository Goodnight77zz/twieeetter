import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';

const String sessionCookieKey = 'sessionCookie';

Dio createSessionDio() {
  final dio = Dio();

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        final prefs = await SharedPreferences.getInstance();
        final cookie = prefs.getString(sessionCookieKey);
        if (cookie != null && cookie.isNotEmpty) {
          options.headers['Cookie'] = cookie;
        }
        handler.next(options);
      },
      onResponse: (response, handler) async {
        await saveSessionCookieFromResponse(response);
        handler.next(response);
      },
      onError: (error, handler) async {
        final response = error.response;
        if (response != null) {
          await saveSessionCookieFromResponse(response);
          if (response.statusCode == 401) {
            await clearSessionState();
          }
        }
        handler.next(error);
      },
    ),
  );

  return dio;
}

Future<void> saveSessionCookieFromResponse(Response response) async {
  final setCookieHeaders = response.headers.map['set-cookie'];
  if (setCookieHeaders == null || setCookieHeaders.isEmpty) {
    return;
  }

  for (final header in setCookieHeaders) {
    final match = RegExp(r'(JSESSIONID=[^;]+)').firstMatch(header);
    if (match != null) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(sessionCookieKey, match.group(1)!);
      return;
    }
  }
}

Future<void> clearSessionState() async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.remove('userId');
  await prefs.remove('username');
  await prefs.remove(sessionCookieKey);
}

Map<String, dynamic> asMap(dynamic value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return value.map((key, item) => MapEntry(key.toString(), item));
  }
  return <String, dynamic>{};
}

List<dynamic> asList(dynamic value) {
  if (value is List) {
    return List<dynamic>.from(value);
  }
  if (value is Map) {
    final map = asMap(value);
    final data = map['data'];
    if (data is List) {
      return List<dynamic>.from(data);
    }
    final rows = map['rows'];
    if (rows is List) {
      return List<dynamic>.from(rows);
    }
    final wrappedValue = map['value'];
    if (wrappedValue is List) {
      return List<dynamic>.from(wrappedValue);
    }
  }
  return const <dynamic>[];
}

List<Map<String, dynamic>> asMapList(dynamic value) {
  return asList(
    value,
  ).map(asMap).where((item) => item.isNotEmpty).toList(growable: false);
}

int asInt(dynamic value, {int fallback = 0}) {
  if (value is int) {
    return value;
  }
  if (value is double) {
    return value.round();
  }
  return int.tryParse(value?.toString() ?? '') ?? fallback;
}

double asDouble(dynamic value, {double fallback = 0}) {
  if (value is double) {
    return value;
  }
  if (value is int) {
    return value.toDouble();
  }
  return double.tryParse(value?.toString() ?? '') ?? fallback;
}
