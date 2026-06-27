# Community Clinic Appointment Portal - User Story Requirements

## Product Brief

The Community Clinic Network wants a patient-facing appointment portal for primary-care visits,
vaccinations, lab follow-ups, and telehealth consultations. The system must reduce phone traffic,
protect patient information, and keep clinic staff in control of provider schedules.

## Goals

- Patients can find and book an appropriate appointment without calling the clinic.
- Clinic schedulers can manage capacity, blocked times, and provider availability.
- Providers can review appointment context before the visit.
- Compliance staff can audit access to protected health information.

## User Stories

### US-01 Search Available Appointments

As a patient, I want to search available appointment slots by clinic, visit reason, provider, and
date range so that I can choose a time that fits my needs.

Acceptance criteria:

- Search results show clinic location, provider name, visit type, earliest start time, and whether
  telehealth is available.
- Patients can filter by language preference and accessibility needs.
- Slots already held or booked are not returned.

### US-02 Book Appointment

As a patient, I want to book a selected slot so that the clinic reserves the time for me.

Acceptance criteria:

- The portal captures patient identity, visit reason, contact preference, and insurance status.
- The selected slot is held for 10 minutes during confirmation.
- A booking confirmation is created only when the patient accepts clinic policies.
- The patient receives a confirmation notification.

### US-03 Cancel or Reschedule Appointment

As a patient, I want to cancel or reschedule my appointment so that another patient can use the
time if I cannot attend.

Acceptance criteria:

- A patient can cancel online until 12 hours before the visit.
- A patient can reschedule by choosing a replacement slot.
- Late cancellations are recorded for clinic review.

### US-04 Manage Provider Availability

As a clinic scheduler, I want to maintain provider schedules, blocked times, and clinic capacity so
that appointment search reflects real operational availability.

Acceptance criteria:

- Schedulers can add working hours, breaks, blocked times, and visit-type limits.
- Schedule changes are audited with scheduler identity and timestamp.
- Existing appointments are protected from accidental deletion.

### US-05 Prepare Visit Context

As a provider, I want to see appointment reason, patient notes, and relevant alerts before the
visit so that I can prepare safely.

Acceptance criteria:

- Provider view shows appointment details and patient-submitted notes.
- Sensitive alerts are visible only to authorized care team members.
- Access to appointment context is logged.

### US-06 Audit Patient Data Access

As a compliance officer, I want access logs for patient appointment data so that privacy reviews can
confirm appropriate use.

Acceptance criteria:

- Every read of appointment details records user, role, patient, timestamp, and reason if supplied.
- Compliance staff can search logs by patient, staff member, clinic, and date range.
- Logs are retained for 6 years.

## Business Rules

- Appointment slots have states: Available, Held, Booked, Cancelled, Completed, No Show.
- A held slot expires after 10 minutes unless booking is confirmed.
- Patients cannot book two primary-care visits that overlap in time.
- Vaccination appointments require vaccine inventory availability.
- Telehealth appointments require verified phone or email contact.
- Minors require guardian contact information.

## Domain Terms

- Appointment Slot: a time interval offered by a provider at a clinic.
- Visit Reason: patient-selected reason such as primary care, vaccination, lab follow-up, or
  telehealth consultation.
- Patient Contact Preference: SMS, email, or phone.
- Provider Schedule: working hours and blocked times for a provider.
- Access Log Entry: audit record of appointment data access.

## Risks and Assumptions

- Identity verification provider is not yet selected.
- SMS delivery is assumed available through an external notification service.
- Multi-language scheduling labels must be reviewed by clinic operations.
- Provider schedules are currently maintained in spreadsheets, so migration quality is uncertain.
