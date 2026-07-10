package com.eservice1.service.service;

import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.service.entity.PortalService;
import com.eservice1.service.repository.PortalServiceRepository;
import com.eservice1.submission.repository.CustomerRequestRepository;
import org.springframework.stereotype.Service;
import com.eservice1.service.dto.CreateServiceRequest;
import com.eservice1.document.entity.RequiredDocument;
import com.eservice1.document.repository.RequiredDocumentRepository;
import java.util.List;
import com.eservice1.common.dto.PageResponseDTO;
import com.eservice1.common.util.PaginationMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalServiceService {
    private final PortalServiceRepository repository;

    private final RequiredDocumentRepository
            documentRepository;
    private final CustomerRequestRepository requestRepository;
    public PortalServiceService(
            PortalServiceRepository repository,
            RequiredDocumentRepository documentRepository,
            CustomerRequestRepository requestRepository) {

        this.repository = repository;
        this.documentRepository = documentRepository;
        this.requestRepository = requestRepository;
    }

    public PortalService save(PortalService service) {
        return repository.save(service);
    }

    public PortalService createService(
            CreateServiceRequest request) {

        PortalService service =
                new PortalService();

        service.setServiceName(
                request.getServiceName()
        );

        service.setDescription(
                request.getDescription()
        );

        service.setActive(
                request.getActive()
        );

        PortalService savedService =
                repository.save(service);

        for (String documentName
                : request.getDocuments()) {

            if (documentName == null
                    || documentName.isBlank()) {
                continue;
            }

            RequiredDocument document =
                    new RequiredDocument();

            document.setDocumentName(
                    documentName
            );

            document.setService(
                    savedService
            );

            documentRepository.save(
                    document
            );
        }

        return savedService;
    }

    public PageResponseDTO<PortalService> getAll(

            int page,

            int size,

            String search

    ) {

        Pageable pageable =

                PageRequest.of(

                        page,

                        size,

                        Sort.by("id").descending()

                );

        if (search == null || search.trim().isBlank()) {

            return PaginationMapper.toResponse(

                    repository.findAll(pageable)

            );

        }

        return PaginationMapper.toResponse(

                repository.searchServices(

                        search.trim(),

                        pageable

                )

        );

    }
    public PageResponseDTO<PortalService> getAll(

            int page,

            int size

    ) {

        return getAll(

                page,

                size,

                null

        );

    }
    public PortalService getById(Long id) {

        PortalService service =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Service not found."
                                )
                        );

        return service;
    }
    @Transactional
    public void delete(Long id) {

        if (requestRepository.existsByService_Id(id)) {

            PortalService service = getById(id);
            service.setActive(false);

            repository.save(service);

            return;
        }

        documentRepository.deleteByService_Id(id);

        repository.deleteById(id);
    }
    public PageResponseDTO<PortalService> getActiveServices(

            int page,

            int size,

            String search

    ) {

        Pageable pageable =

                PageRequest.of(

                        page,

                        size,

                        Sort.by("id").descending()

                );

        if (search == null || search.trim().isBlank()) {

            return PaginationMapper.toResponse(

                    repository.findByActiveTrue(pageable)

            );

        }

        return PaginationMapper.toResponse(

                repository.searchActiveServices(

                        search.trim(),

                        pageable

                )

        );

    }
}