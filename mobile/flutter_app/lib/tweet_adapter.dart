Map<String, dynamic>? normalizeTweetItem(dynamic item) {
  if (item == null) {
    return null;
  }

  final rawItem = _asMap(item);
  if (rawItem == null) {
    return null;
  }

  final tweet = _asMap(rawItem['tweet']) ?? Map<String, dynamic>.from(rawItem);
  final signals = _asMap(rawItem['signals']) ?? const <String, dynamic>{};
  final radar = _asMap(signals['radar']) ?? const <String, dynamic>{};

  final normalized = Map<String, dynamic>.from(tweet);
  normalized['signals'] = signals;
  normalized['radar'] = radar;
  normalized['likeCount'] = _readInt(
    signals,
    'likes',
    fallback: normalized['likeCount'],
  );
  normalized['commentCount'] = _readInt(
    signals,
    'comments',
    fallback: normalized['commentCount'],
  );
  normalized['downloadCount'] = _readInt(
    signals,
    'downloads',
    fallback: normalized['downloadCount'],
  );
  normalized['viewCount'] = _readInt(
    signals,
    'views',
    fallback: normalized['viewCount'],
  );
  normalized['shareCount'] = _readInt(
    signals,
    'shares',
    fallback: normalized['shareCount'],
  );
  normalized['bookmarkCount'] = _readInt(
    signals,
    'bookmarks',
    fallback: normalized['bookmarkCount'],
  );
  normalized['avgScore'] = _readDouble(
    signals,
    'averageScore',
    fallback: normalized['avgScore'],
  );
  normalized['hotScore'] = _readDouble(
    signals,
    'hotScore',
    fallback: normalized['hotScore'],
  );
  normalized['ratingCount'] = _readInt(
    signals,
    'ratingCount',
    fallback: normalized['ratingCount'],
  );
  normalized['expertCount'] = _readInt(
    signals,
    'expertCount',
    fallback: normalized['expertCount'],
  );
  normalized['expertScore'] = _readDouble(
    signals,
    'expertScore',
    fallback: normalized['expertScore'],
  );
  return normalized;
}

Map<String, dynamic>? _asMap(dynamic value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return value.map((key, item) => MapEntry(key.toString(), item));
  }
  return null;
}

int _readInt(Map<String, dynamic> source, String key, {dynamic fallback}) {
  final value = source[key] ?? fallback;
  if (value is int) {
    return value;
  }
  if (value is double) {
    return value.round();
  }
  return int.tryParse(value?.toString() ?? '') ?? 0;
}

double _readDouble(
  Map<String, dynamic> source,
  String key, {
  dynamic fallback,
}) {
  final value = source[key] ?? fallback;
  if (value is double) {
    return value;
  }
  if (value is int) {
    return value.toDouble();
  }
  return double.tryParse(value?.toString() ?? '') ?? 0;
}
