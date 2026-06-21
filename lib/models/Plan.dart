class Plan {
  int? planId; // Changed to int? for auto-increment
  String planName;
  String planDay;

  Plan({
    this.planId,
    required this.planName,
    required this.planDay,
  });

  // Convert Plan object to Map for Database
  Map<String, dynamic> toMap() {
    return {
      'planId': planId,
      'planName': planName,
      'planDay': planDay,
    };
  }

  // Convert Map from Database back to Plan object
  factory Plan.fromMap(Map<String, dynamic> map) {
    return Plan(
      planId: map['planId'],
      planName: map['planName'],
      planDay: map['planDay'],
    );
  }
}