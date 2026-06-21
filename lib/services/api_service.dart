import 'package:dio/dio.dart';

class ApiService {
  static final String baseUrl = "https://notificationtest-99357-default-rtdb.firebaseio.com/"; // Replace with your base URL

  final Dio _dio = Dio(
    BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ),
  );

  Dio get dio => _dio;

  // You can add interceptors here for token management
  ApiService() {
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        // Add token if available
        return handler.next(options);
      },
      onError: (DioException e, handler) {
        return handler.next(e);
      },
    ));
  }
}
