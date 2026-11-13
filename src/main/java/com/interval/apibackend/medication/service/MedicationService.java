package com.interval.apibackend.medication.service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;

import com.interval.apibackend.common.exception.NotFoundException;
import com.interval.apibackend.common.response.PageResponse;
import com.interval.apibackend.medication.dto.CreateMedicationRequest;
import com.interval.apibackend.medication.dto.MedicationResponse;
import com.interval.apibackend.medication.dto.UpdateMedicationRequest;
import com.interval.apibackend.medication.entity.DosageForm;
import com.interval.apibackend.medication.entity.Medication;
import com.interval.apibackend.medication.event.MedicationLifecycleEvent;
import com.interval.apibackend.medication.event.MedicationLifecycleEvent.Type;
import com.interval.apibackend.medication.repository.MedicationRepository;
import com.interval.apibackend.user.service.UserTimezoneService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private static final String NOT_FOUND = "Medication not found";

    private final MedicationRepository medicationRepository;
    private final UserTimezoneService timezoneService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<MedicationResponse> list(UUID userId, int page, int size, Boolean active) {
        // Tie-breaker id agar urutan antar halaman stabil jika createdAt sama.
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));

        Page<Medication> result = (active == null)
                ? medicationRepository.findByUserIdAndDeletedAtIsNull(userId, pageable)
                : medicationRepository.findByUserIdAndActiveAndDeletedAtIsNull(userId, active, pageable);

        ZoneId zone = timezoneService.zoneOf(userId);
        return PageResponse.from(result, medication -> MedicationResponse.from(medication, zone));
    }

    @Transactional
    public MedicationResponse create(UUID userId, CreateMedicationRequest request) {
        Medication medication = Medication.create(
                userId,
                request.name().trim(),
                blankToNull(request.dosage()),
                request.dosageForm() != null ? request.dosageForm() : DosageForm.OTHER,
                blankToNull(request.instructions()),
                blankToNull(request.notes()),
                blankToNull(request.imageUrl()));

        // saveAndFlush agar createdAt/updatedAt sudah terisi saat response dibentuk.
        medicationRepository.saveAndFlush(medication);
        return MedicationResponse.from(medication, timezoneService.zoneOf(userId));
    }

    @Transactional(readOnly = true)
    public MedicationResponse get(UUID userId, UUID medicationId) {
        return MedicationResponse.from(find(userId, medicationId), timezoneService.zoneOf(userId));
    }

    @Transactional
    public MedicationResponse update(UUID userId, UUID medicationId, UpdateMedicationRequest request) {
        Medication medication = find(userId, medicationId);
        boolean wasActive = medication.isActive();

        // Field wajib: null = tidak diubah.
        if (request.getName() != null) {
            medication.setName(request.getName().trim());
        }
        if (request.getDosageForm() != null) {
            medication.setDosageForm(request.getDosageForm());
        }
        if (request.getActive() != null) {
            medication.setActive(request.getActive());
        }
        // Field nullable: dikirim (walau null) = ubah/kosongkan; tidak dikirim = biarkan.
        if (request.isDosagePresent()) {
            medication.setDosage(blankToNull(request.getDosage()));
        }
        if (request.isInstructionsPresent()) {
            medication.setInstructions(blankToNull(request.getInstructions()));
        }
        if (request.isNotesPresent()) {
            medication.setNotes(blankToNull(request.getNotes()));
        }
        if (request.isImageUrlPresent()) {
            medication.setImageUrl(blankToNull(request.getImageUrl()));
        }

        // flush agar updatedAt terbaru ikut di response (tidak ada UPDATE jika tidak ada yang berubah)
        medicationRepository.saveAndFlush(medication);

        if (wasActive != medication.isActive()) {
            publish(userId, medicationId, medication.isActive() ? Type.RESUMED : Type.PAUSED);
        }
        return MedicationResponse.from(medication, timezoneService.zoneOf(userId));
    }

    /** Soft delete: baris dan riwayat konsumsinya tetap ada, hanya disembunyikan. */
    @Transactional
    public void delete(UUID userId, UUID medicationId) {
        Medication medication = find(userId, medicationId);
        medication.setDeletedAt(clock.instant());
        // Pengaman berlapis: query generator yang hanya memeriksa active tidak akan menyentuh obat terhapus.
        medication.setActive(false);
        publish(userId, medicationId, Type.DELETED);
    }

    private Medication find(UUID userId, UUID medicationId) {
        // Milik orang lain, sudah dihapus, atau tidak ada: semuanya 404 yang sama.
        return medicationRepository.findByIdAndUserIdAndDeletedAtIsNull(medicationId, userId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    private void publish(UUID userId, UUID medicationId, Type type) {
        eventPublisher.publishEvent(new MedicationLifecycleEvent(userId, medicationId, type));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
