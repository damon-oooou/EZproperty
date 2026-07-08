package com.propertymap.controller;

import com.propertymap.controller.dto.ReportDetailsResponse;
import com.propertymap.controller.dto.RoomConditionResponse;
import com.propertymap.controller.dto.RoomConditionUpdate;
import com.propertymap.controller.dto.UpdateReportDetailsRequest;
import com.propertymap.service.PdfReportService;
import com.propertymap.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 报告相关端点。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final PdfReportService pdfReportService;

    @GetMapping("/inspections/{inspectionId}/conditions")
    public List<RoomConditionResponse> getConditions(@PathVariable Long inspectionId) {
        return reportService.getConditions(inspectionId);
    }

    @PutMapping("/inspections/{inspectionId}/conditions")
    public List<RoomConditionResponse> updateConditions(@PathVariable Long inspectionId,
                                                        @RequestBody List<RoomConditionUpdate> updates) {
        return reportService.updateConditions(inspectionId, updates);
    }

    @GetMapping("/inspections/{inspectionId}/report-details")
    public ReportDetailsResponse getReportDetails(@PathVariable Long inspectionId) {
        return reportService.getReportDetails(inspectionId);
    }

    @PutMapping("/inspections/{inspectionId}/report-details")
    public ReportDetailsResponse updateReportDetails(@PathVariable Long inspectionId,
                                                     @RequestBody UpdateReportDetailsRequest request) {
        return reportService.updateReportDetails(inspectionId, request);
    }

    /** v0.5 阶段三:生成并下载 PDF 报告。 */
    @GetMapping("/inspections/{inspectionId}/report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long inspectionId) {
        PdfReportService.GeneratedReport report = pdfReportService.generate(inspectionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.fileName() + "\"")
                .body(report.content());
    }
}