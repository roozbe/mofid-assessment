package net.roozbe.mofidassessment.mapper;

import net.roozbe.mofidassessment.controller.request.AccountRequest;
import net.roozbe.mofidassessment.controller.response.AccountResponse;
import net.roozbe.mofidassessment.da.entity.AccountEntity;
import net.roozbe.mofidassessment.service.model.AccountModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "accountId", ignore = true)
    AccountModel toAccountModel(AccountRequest request);

    @Mapping(source = "id", target = "accountId")
    AccountModel toAccountModel(AccountEntity entity);

    AccountResponse toAccountResponse(AccountModel model);
}
