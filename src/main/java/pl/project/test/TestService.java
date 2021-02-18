package pl.project.test;

import com.github.javafaker.Faker;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import pl.project.components.*;
import pl.project.execDetails.*;

import java.util.*;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
public class TestService {
    Logger log = LogManager.getLogger(this.getClass());
    Faker faker = new Faker();
    @Autowired
    TestRepository testRepository;

    private final RestTemplate restTemplate;
    Random random = new Random();

    public TestService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public List<Test> getAllTest() {
        List<Test> testList = new ArrayList<>();
        testRepository.findAll().forEach(testList::add);
        return testList;
    }

    public Test getTest(Integer id) {
        return testRepository.findById(id).get();
    }

    public Test addUpdateTest(Test test) {
        return testRepository.save(test);
    }

    public void deleteTest(Integer id) {
        testRepository.deleteById(id);
    }

    public ExecDetails signOnUsers(Integer numberUsers) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        List<User> userList = createNewUserList(numberUsers, 10000);
        HttpEntity<Object> requestEntity = new HttpEntity<Object>(userList, headers);
        ResponseEntity<ExecDetails> response = this.restTemplate.postForEntity("http://et-api:8080/user/addUserList", requestEntity, ExecDetails.class);
        return response.getBody();
    }

    public List<User> createNewUserList(Integer numberUsers, Integer startUserMoney) {
        List<User> userList = new ArrayList<>();
        for (int i = 0; i < numberUsers; i++) {
            userList.add(new User(faker.name().firstName(), faker.name().lastName(), "email" + i + "@test.com", String.valueOf((new Date()).getTime() + faker.number().randomDigit()), Float.valueOf(startUserMoney)));
        }
        return userList;
    }

    public ExecDetailsUser signOnUsers(List<User> userList) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<Object> requestEntity = new HttpEntity<Object>(userList, headers);
        ResponseEntity<ExecDetailsUser> response = this.restTemplate.postForEntity("http://et-api:8080/user/addUserList", requestEntity, ExecDetailsUser.class);
        return response.getBody();
    }

    public ExecDetailsUser getUserById(Integer userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<ExecDetailsUser> response = this.restTemplate.getForEntity("http://et-api:8080/user/details/" + userId, ExecDetailsUser.class);
        return response.getBody();
    }

    private ExecDetailsCompany getCompanyInfoList() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<ExecDetailsCompany> response = this.restTemplate.getForEntity("http://et-api:8080/company/infoList", ExecDetailsCompany.class);
        return response.getBody();
    }

    public TestDetails simulate(@NonNull Integer numberUser, @NonNull Integer numberSeries, Integer companyId,
                                String companyName, @NonNull Integer startUserMoney, @NonNull Integer startStockNumber, @NonNull Date dateTrade) {
        ExecDetailsCompany execDetailsCompany = getCompanyInfoList();
        CompanyInfoDTO companyInfo = getCompany(companyId, companyName, execDetailsCompany.getCompanyIdList());
        companyId = companyInfo.getCompanyId();
        TestDetails testDetails = new TestDetails(new ExecDetails(0, 0),
                new PriceDetails(100000f, 0f, 100000f, 0f, 0),
                0, companyInfo.getCompanyName());
        updateExecDetails(testDetails.getExecDetails(), execDetailsCompany.getExecDetails());
        ExecDetailsUser execDetailsUser = signOnUsers(createNewUserList(numberUser, startUserMoney));
        List<User> userList = execDetailsUser.getUserList();
        updateExecDetails(testDetails.getExecDetails(), new ExecDetails(execDetailsUser.getExeTime(), execDetailsUser.getDbTime()));
        updateTransactionExecDetails(testDetails, buyStocks(userList, startStockNumber, true, companyId, dateTrade));
        for (int i = 0; i < numberSeries; i++) {
            updateExecDetails(testDetails.getExecDetails(), addSellLimitOffer(userList, startStockNumber, companyId, dateTrade));
            updateExecDetails(testDetails.getExecDetails(), addBuyLimitOffer(userList, startStockNumber, companyId, dateTrade));
            updateTransactionExecDetails(testDetails, sellStocks(userList, startStockNumber, companyId, dateTrade));
            updateTransactionExecDetails(testDetails, buyStocks(userList, startStockNumber, false, companyId, dateTrade));
        }
        return testDetails;
    }

    private CompanyInfoDTO getCompany(Integer companyId, String companyName, List<CompanyInfoDTO> companyInfoList) {
        return isNull(companyId) || !companyInfoList.contains(getCompanyInfoByCompanyId(companyInfoList, companyId)) ? getRandomCompanyInfo(companyInfoList) : new CompanyInfoDTO(companyId, companyName);
    }

    private CompanyInfoDTO getCompanyInfoByCompanyId(List<CompanyInfoDTO> companyInfoList, Integer companyId) {
        return companyInfoList.stream().filter(companyInfoDTO -> companyInfoDTO.getCompanyId().equals(companyId)).findFirst().get();
    }

    private CompanyInfoDTO getRandomCompanyInfo(List<CompanyInfoDTO> companyInfoList) {
        return companyInfoList.get(random.nextInt(companyInfoList.size()));
    }

    private List<User> createRandomUserList(List<User> userList) {
        List<User> randomUserList = new LinkedList<>();
        randomUserList.addAll(userList);
        for (int i = 0; i < userList.size() / 2; i++) {
            int randomIndex = random.nextInt(randomUserList.size());
            randomUserList.remove(randomIndex);
        }
        return randomUserList;
    }

    public ExecDetails updateExecDetails(ExecDetails mainExecDetails, ExecDetails newExecDetails) {
        mainExecDetails.setDbTime(mainExecDetails.getDbTime() + newExecDetails.getDbTime());
        mainExecDetails.setExeTime(mainExecDetails.getExeTime() + newExecDetails.getExeTime());
        return mainExecDetails;
    }

    public TestDetails updateTransactionExecDetails(TestDetails mainExecDetails, TransactionDetails transactionDetails) {
        if (nonNull(transactionDetails.getPrice()) && transactionDetails.getPrice() > 0) {
            if (transactionDetails.getType().equals("Sell")) {
                if (mainExecDetails.getPriceDetails().getMaxSellPrice() < transactionDetails.getPrice()) {
                    mainExecDetails.getPriceDetails().setMaxSellPrice(transactionDetails.getPrice());
                }
                if (mainExecDetails.getPriceDetails().getMinSellPrice() > transactionDetails.getPrice()) {
                    mainExecDetails.getPriceDetails().setMinSellPrice(transactionDetails.getPrice());
                }
            } else {
                if (mainExecDetails.getPriceDetails().getMaxBuyPrice() < transactionDetails.getPrice()) {
                    mainExecDetails.getPriceDetails().setMaxBuyPrice(transactionDetails.getPrice());
                }
                if (mainExecDetails.getPriceDetails().getMinBuyPrice() > transactionDetails.getPrice()) {
                    mainExecDetails.getPriceDetails().setMinBuyPrice(transactionDetails.getPrice());
                }
            }
        }
        mainExecDetails.getPriceDetails().setVolumes(mainExecDetails.getPriceDetails().getVolumes() + transactionDetails.getAmount());
        mainExecDetails.setNumberOfRequests(mainExecDetails.getNumberOfRequests() + transactionDetails.getNumberRequest());

        mainExecDetails.getExecDetails().setDbTime(mainExecDetails.getExecDetails().getDbTime() + transactionDetails.getExecDetails().getDbTime());
        mainExecDetails.getExecDetails().setExeTime(mainExecDetails.getExecDetails().getExeTime() + transactionDetails.getExecDetails().getExeTime());
        return mainExecDetails;
    }

    public TransactionDetails buyStocks(List<User> userList, Integer stockNumber, boolean buyAll, Integer companyId, Date dateTrade) {
        TransactionDetails transactionDetails = new TransactionDetails(0, 0f, 0, "Buy", new ExecDetails(0, 0));
        try {
            for (User user : userList) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                ExecDetailsUser execDetailsUser = getUserById(user.getId());
                updateExecDetails(transactionDetails.getExecDetails(), new ExecDetails(execDetailsUser.getExeTime(), execDetailsUser.getDbTime()));
                ExecDetailsOfferLimit execDetailsOfferLimit = getOfferLimitList(companyId, "Sell", user.getId());
                updateExecDetails(transactionDetails.getExecDetails(), execDetailsOfferLimit.getExecDetails());
                if (execDetailsOfferLimit.getOfferLimitDTOList().isEmpty()) {
                    break;
                }
                int amountStockFromOfferLimit = getAmountStockFromOfferLimit(execDetailsOfferLimit.getOfferLimitDTOList());
                int amount = buyAll ? stockNumber : random.nextInt(stockNumber/2) + 1;
                float price = 0;
                if (execDetailsUser.getUser().getCash() <= 0 || amountStockFromOfferLimit == 0) {
                    amount = 0;
                } else {
                    price = getPriceStockFromOfferLimitByAmount(execDetailsOfferLimit.getOfferLimitDTOList(), amount, user.getCash());
                    if (price > user.getCash()) {
                        amount -= getAvailableAmountStockFromOfferLimit(execDetailsOfferLimit.getOfferLimitDTOList(), amount, user.getCash());
                    }
                }
                if (amount > 0) {
                    transactionDetails.setAmount(amount);
                    transactionDetails.setPrice(getOfferSettledByAmount(execDetailsOfferLimit.getOfferLimitDTOList(), amount).getPrice());
                    StockSellBuy stockBuy = new StockSellBuy(amount, "Buy", dateTrade, companyId, user.getId());
                    HttpEntity<Object> requestEntity = new HttpEntity<Object>(stockBuy, headers);
                    this.restTemplate.postForEntity("http://et-api:8080/offerSellBuy/newOffer", requestEntity, ExecDetails.class);
                }
            }
        } catch (Exception e) {
            log.error("BuyStocks: " + e.getCause());
        }
        return transactionDetails;
    }

    public TransactionDetails sellStocks(List<User> userList, Integer stockNumber, Integer companyId, Date dateTrade) {
        TransactionDetails transactionDetails = new TransactionDetails(0, 0f, 0, "Sell", new ExecDetails(0, 0));
        try {
            for (User user : userList) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                ExecDetailsUserStock execDetailsUserStock = getStockByUserIdAndCompanyId(user.getId(), companyId);
                updateExecDetails(transactionDetails.getExecDetails(), execDetailsUserStock.getExecDetails());
                UserStockDTO userStockDTO = execDetailsUserStock.getUserStockDTO();
                ExecDetailsOfferLimit execDetailsOfferLimit = getOfferLimitList(companyId, "Buy", user.getId());
                updateExecDetails(transactionDetails.getExecDetails(), execDetailsOfferLimit.getExecDetails());
                if (execDetailsOfferLimit.getOfferLimitDTOList().isEmpty()) {
                    break;
                }
                int amount = 0;
                if (userStockDTO.getAmount() > 0) {
                    amount = random.nextInt(stockNumber) + 1;
                }
                if (amount > userStockDTO.getAmount()) {
                    amount = userStockDTO.getAmount();
                }
                int amountStockOfferLimit = getAmountStockFromOfferLimit(execDetailsOfferLimit.getOfferLimitDTOList());
                if (amount > amountStockOfferLimit) {
                    amount = amountStockOfferLimit;
                }
                if (amount > 0) {
                    transactionDetails.setAmount(amount);
                    transactionDetails.setPrice(getOfferSettledByAmount(execDetailsOfferLimit.getOfferLimitDTOList(), amount).getPrice());
                    StockSellBuy stockSell = new StockSellBuy(amount, "Sell", dateTrade, companyId, user.getId());
                    HttpEntity<Object> requestEntity = new HttpEntity<Object>(stockSell, headers);
                    this.restTemplate.postForEntity("http://et-api:8080/offerSellBuy", requestEntity, ExecDetails.class);
                }
            }
        } catch (Exception e) {
            log.error("SellStocks: " + e.getLocalizedMessage());
        }
        return transactionDetails;
    }


    public ExecDetails addBuyLimitOffer(List<User> userList, Integer stockNumber, Integer companyId, Date dateTrade) {
        ExecDetails execDetails = new ExecDetails(0, 0);
        try {
            for (User user : userList) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                ExecDetailsOfferLimit execDetailsSellOfferLimit = getFirstOfferLimit(companyId, "Sell", user.getId());
                updateExecDetails(execDetails, execDetailsSellOfferLimit.getExecDetails());
                int amount = 0;
                float price = 0;
                if (isNull(execDetailsSellOfferLimit.getOfferLimitDTO().getPrice()) || execDetailsSellOfferLimit.getOfferLimitDTO().getPrice() == 0) {
                    ExecDetailsOfferLimit execDetailsBuyOfferLimit = getFirstOfferLimit(companyId, "Buy", user.getId());
                    updateExecDetails(execDetails, execDetailsBuyOfferLimit.getExecDetails());
                    price = execDetailsBuyOfferLimit.getOfferLimitDTO().getPrice() * (1 - ((float) (random.nextInt(5) + 1) / 100));
                } else {
                    price = execDetailsSellOfferLimit.getOfferLimitDTO().getPrice() * (1 - ((float) random.nextInt(5) + 1) / 100);
                }
                ExecDetailsUser execDetailsUser = getUserById(user.getId());
                updateExecDetails(execDetails, new ExecDetails(execDetailsUser.getExeTime(), execDetailsUser.getDbTime()));
                price = (float) (Math.round(price * 100.0) / 100.0);
                amount = random.nextInt(stockNumber) + 1;
                if (execDetailsUser.getUser().getCash() <= 0 || (int) (execDetailsUser.getUser().getCash() / price) <= 0) {
                    amount = 0;
                } else if (execDetailsUser.getUser().getCash() < amount * price) {
                    amount = (random.nextInt((int) (execDetailsUser.getUser().getCash() / price))) + 1;
                }
                if (amount > 0 && price > 0) {
                    OfferLimitDTO stockBuy = new OfferLimitDTO(amount, "Buy", dateTrade, companyId, user.getId(), price);
                    HttpEntity<Object> requestEntity = new HttpEntity<Object>(stockBuy, headers);
                    this.restTemplate.postForEntity("http://et-api:8080/offerSellBuyLimit/newOfferLimit", requestEntity, ExecDetails.class);
                }
            }
        } catch (Exception e) {
            log.error("AddBuyLimitOffer: " + e.getLocalizedMessage());
        }
        return execDetails;
    }

    public ExecDetails addSellLimitOffer(List<User> userList, Integer stockNumber, Integer companyId, Date dateTrade) {
        ExecDetails execDetails = new ExecDetails(0, 0);
        try {
            for (User user : userList) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                ExecDetailsUserStock execDetailsUserStock = getStockByUserIdAndCompanyId(user.getId(), companyId);
                updateExecDetails(execDetails, execDetailsUserStock.getExecDetails());
                int amount;
                float price;
                if (isNull(execDetailsUserStock.getUserStockDTO().getActualPrice()) || execDetailsUserStock.getUserStockDTO().getActualPrice() == 0) {
                    ExecDetailsOfferLimit execDetailsOfferLimit = getFirstOfferLimit(companyId, "Buy", user.getId());
                    updateExecDetails(execDetails, execDetailsOfferLimit.getExecDetails());
                    price = execDetailsOfferLimit.getOfferLimitDTO().getPrice() * (1 + ((float) (random.nextInt(5) + 1) / 100));
                } else {
                    price = execDetailsUserStock.getUserStockDTO().getActualPrice() * (1 + ((float) (random.nextInt(5) + 1) / 100));
                }
                price = (float) (Math.round(price * 100.0) / 100.0);
                if (execDetailsUserStock.getUserStockDTO().getAmount() == 0) {
                    amount = 0;
                } else if (execDetailsUserStock.getUserStockDTO().getAmount() >= stockNumber) {
                    amount = random.nextInt(stockNumber) + 1;
                } else {
                    amount = random.nextInt(execDetailsUserStock.getUserStockDTO().getAmount()) + 1;
                }
                if (amount > 0 && price > 0) {
                    OfferLimitDTO stockBuy = new pl.project.components.OfferLimitDTO(amount, "Sell", dateTrade, companyId, user.getId(), price);
                    HttpEntity<Object> requestEntity = new HttpEntity<Object>(stockBuy, headers);
                    this.restTemplate.postForEntity("http://et-api:8080/offerSellBuyLimit/newOfferLimit", requestEntity, ExecDetails.class);
                }
            }
        } catch (Exception e) {
            log.error("AddSellLimitOffer: " + e.getLocalizedMessage());
        }
        return execDetails;
    }

    private OfferLimitDTO getOfferSettledByAmount(List<OfferLimitDTO> offerLimitDTOList, int amount) {
        for (OfferLimitDTO offerLimitDTO : offerLimitDTOList) {
            if (offerLimitDTO.getAmount() >= amount) {
                return offerLimitDTO;
            } else {
                amount -= offerLimitDTO.getAmount();
            }
        }
        return new OfferLimitDTO();
    }

    private int getAvailableAmountStockFromOfferLimit(List<OfferLimitDTO> offerLimitDTOList, int amount, float cash) {
        float price = 0;
        int amountStock = 0;
        for (OfferLimitDTO offerLimitDTO : offerLimitDTOList) {
            float tempPrice = 0;
            if (offerLimitDTO.getAmount() > amountStock) {
                tempPrice = offerLimitDTO.getPrice() * amountStock;
                amountStock = 0;
            } else {
                tempPrice = offerLimitDTO.getPrice() * offerLimitDTO.getAmount();
                amountStock -= offerLimitDTO.getAmount();
            }
            if (price + tempPrice > cash) {
                for (int i = 1; i <= offerLimitDTO.getAmount() && i <= amount; i++) {
                    if (price + i * offerLimitDTO.getPrice() > cash) {
                        return amountStock - (i - 1);
                    }
                }
                return amountStock;
            }
            if (amountStock <= 0) {
                return amountStock;
            } else {
                amount -= amountStock;
            }
            price += tempPrice;
        }
        return amount;
    }

    private float getPriceStockFromOfferLimitByAmount(List<OfferLimitDTO> offerLimitDTOList, int amount, float cash) {
        float price = 0f;
        int amountStock = 0;
        for (OfferLimitDTO offerLimitDTO : offerLimitDTOList) {
            float tempPrice = 0f;
            if (offerLimitDTO.getAmount() > amountStock) {
                tempPrice = offerLimitDTO.getPrice() * amountStock;
                amountStock = 0;
            } else {
                tempPrice = offerLimitDTO.getPrice() * offerLimitDTO.getAmount();
                amountStock -= offerLimitDTO.getAmount();
            }
            if (price + tempPrice <= cash) {
                for (int i = 1; i <= offerLimitDTO.getAmount() && i <= amount; i++) {
                    if (price + i * offerLimitDTO.getPrice() > cash) {
                        return price + (i - 1) * offerLimitDTO.getPrice();
                    }
                }
                return price + amount * offerLimitDTO.getPrice();
            }
            if (amountStock <= 0) {
                return price;
            } else {
                amount -= amountStock;
            }
            price += tempPrice;
        }
        return price;
    }

    private int getAmountStockFromOfferLimit(List<OfferLimitDTO> offerLimitDTOList) {
        return offerLimitDTOList.stream().mapToInt(OfferLimitDTO::getAmount).sum();
    }

    public ExecDetailsOfferLimit getOfferLimitList(Integer companyId, String type, Integer userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (type.equals("Buy")) {
            ResponseEntity<ExecDetailsOfferLimit> response = this.restTemplate.getForEntity("http://et-api:8080/offerSellBuyLimit/buy/company/details/" + companyId + "/user/" + userId
                    , ExecDetailsOfferLimit.class);
            return new ExecDetailsOfferLimit(response.getBody().getExecDetails(), response.getBody().getOfferLimitDTOList());
        } else {
            ResponseEntity<ExecDetailsOfferLimit> response = this.restTemplate.getForEntity("http://et-api:8080/offerSellBuyLimit/sell/company/details/" + companyId + "/user/" + userId
                    , ExecDetailsOfferLimit.class);
            return new ExecDetailsOfferLimit(response.getBody().getExecDetails(), response.getBody().getOfferLimitDTOList());
        }
    }

    public ExecDetailsOfferLimit getFirstOfferLimit(Integer companyId, String type, Integer userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (type.equals("Buy")) {
            ResponseEntity<ExecDetailsOfferLimit> response = this.restTemplate.getForEntity("http://et-api:8080/offerSellBuyLimit/buy/company/first/" + companyId + "/user/" + userId
                    , ExecDetailsOfferLimit.class);
            return new ExecDetailsOfferLimit(response.getBody().getExecDetails(), response.getBody().getOfferLimitDTO());
        } else {
            ResponseEntity<ExecDetailsOfferLimit> response = this.restTemplate.getForEntity("http://et-api:8080/offerSellBuyLimit/sell/company/first/" + companyId + "/user/" + userId
                    , ExecDetailsOfferLimit.class);
            return new ExecDetailsOfferLimit(response.getBody().getExecDetails(), response.getBody().getOfferLimitDTO());
        }
    }

    public ExecDetailsUserStock getStockByUserIdAndCompanyId(Integer userId, Integer companyId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<ExecDetailsUserStock> response = this.restTemplate.getForEntity("http://et-api:8080/stock/company/" + companyId + "/user/" + userId, ExecDetailsUserStock.class);
        return new ExecDetailsUserStock(response.getBody().getExecDetails(), response.getBody().getUserStockDTO());
    }

}
